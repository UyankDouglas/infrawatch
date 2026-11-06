package com.infrawatch.reports;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.common.ApiException;
import com.infrawatch.incidents.Incident;
import com.infrawatch.incidents.IncidentRepository;
import com.infrawatch.metrics.MetricRollupRepository;
import com.infrawatch.metrics.MetricSampleRepository;
import com.infrawatch.metrics.SeriesPoint;
import com.infrawatch.monitoring.AvailabilityCalculator;
import com.infrawatch.monitoring.AvailabilityCalculator.MaintenanceMode;
import com.infrawatch.monitoring.HealthCheckExecutionRepository;
import com.infrawatch.monitoring.HealthCheckExecutionRepository.BucketStats;
import com.infrawatch.monitoring.HealthCheckExecutionRepository.ResourceLatencyStats;
import com.infrawatch.monitoring.Periods;
import com.infrawatch.monitoring.ResourceStatusHistory;
import com.infrawatch.monitoring.ResourceStatusHistoryRepository;
import com.infrawatch.reports.ReportDtos.AvailabilityReport;
import com.infrawatch.reports.ReportDtos.IncidentRow;
import com.infrawatch.reports.ReportDtos.IncidentsReport;
import com.infrawatch.reports.ReportDtos.LatencyPoint;
import com.infrawatch.reports.ReportDtos.MetricTrendPoint;
import com.infrawatch.reports.ReportDtos.ResourceAvailabilityRow;
import com.infrawatch.reports.ReportDtos.ResourceReport;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import com.infrawatch.security.OrganizationContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatórios calculados a partir dos dados persistidos.
 *
 * Definições (documentadas em docs/alerts-and-incidents.md e docs/reports.md):
 *  - Disponibilidade (%) = tempo disponível / tempo observado × 100, somente intervalos com estado conhecido;
 *    manutenção excluída (padrão) ou contada como indisponibilidade.
 *  - Tempo indisponível = tempo observado em INDISPONÍVEL.
 *  - MTTR = média de (resolvido − aberto) dos incidentes resolvidos no período.
 *  - MTBF = média do intervalo entre o fim de um incidente e o início do seguinte, por recurso; exige ≥ 2 incidentes.
 *  - MTTA = média de (reconhecido − aberto) dos incidentes reconhecidos no período.
 */
@Service
public class ReportService {

    public static final String AVAILABILITY_FORMULA =
            "Disponibilidade (%) = tempo disponível ÷ tempo observado × 100. Tempo observado = intervalos com estado conhecido "
            + "(Disponível, Degradado, Indisponível); Aguardando/Sem dados são sempre excluídos; Degradado conta como disponível.";

    private final MonitoredResourceRepository resources;
    private final ResourceStatusHistoryRepository history;
    private final HealthCheckExecutionRepository executions;
    private final IncidentRepository incidents;
    private final MetricSampleRepository samples;
    private final MetricRollupRepository rollups;
    private final UserRepository users;

    public ReportService(MonitoredResourceRepository resources, ResourceStatusHistoryRepository history,
                         HealthCheckExecutionRepository executions, IncidentRepository incidents,
                         MetricSampleRepository samples, MetricRollupRepository rollups, UserRepository users) {
        this.resources = resources;
        this.history = history;
        this.executions = executions;
        this.incidents = incidents;
        this.samples = samples;
        this.rollups = rollups;
        this.users = users;
    }

    public static MaintenanceMode mode(String maintenance) {
        return "counted".equalsIgnoreCase(maintenance) ? MaintenanceMode.COUNTED_AS_DOWNTIME : MaintenanceMode.EXCLUDED;
    }

    @Transactional(readOnly = true)
    public AvailabilityReport availability(OrganizationContext ctx, String period, String maintenance, UUID groupId) {
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);
        MaintenanceMode mode = mode(maintenance);

        List<MonitoredResource> all = resources.findByOrganizationIdOrderByNameAsc(ctx.organizationId()).stream()
                .filter(r -> groupId == null || (r.getGroup() != null && r.getGroup().getId().equals(groupId)))
                .toList();
        Map<UUID, List<ResourceStatusHistory>> transitions = history
                .findByOrganizationIdAndChangedAtGreaterThanEqualOrderByChangedAtAsc(ctx.organizationId(), from).stream()
                .collect(Collectors.groupingBy(ResourceStatusHistory::getResourceId));
        Map<UUID, ResourceLatencyStats> latency = executions.latencyStatsPerResource(ctx.organizationId(), from, to).stream()
                .collect(Collectors.toMap(ResourceLatencyStats::getResourceId, s -> s));
        List<Incident> periodIncidents = incidents.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("organizationId"), ctx.organizationId()),
                cb.greaterThanOrEqualTo(root.get("openedAt"), from)));
        Map<UUID, List<Incident>> incidentsByResource = periodIncidents.stream()
                .filter(i -> i.getResourceId() != null)
                .collect(Collectors.groupingBy(Incident::getResourceId));

        List<ResourceAvailabilityRow> rows = new ArrayList<>();
        long observedTotal = 0, availableTotal = 0, unavailableTotal = 0;
        for (MonitoredResource r : all) {
            List<ResourceStatusHistory> own = transitions.getOrDefault(r.getId(), List.of());
            Instant start = r.getCreatedAt().isAfter(from) ? r.getCreatedAt() : from;
            ResourceStatus atStart = own.isEmpty() ? r.getCurrentStatus() : own.getFirst().getFromStatus();
            AvailabilityCalculator.Result res = AvailabilityCalculator.compute(start, to, atStart, own, mode);
            long maintenanceSeconds = secondsIn(start, to, atStart, own, ResourceStatus.MAINTENANCE);
            long unavailable = res.observedSeconds() - res.availableSeconds();
            ResourceLatencyStats lat = latency.get(r.getId());
            List<Incident> inc = incidentsByResource.getOrDefault(r.getId(), List.of());
            Mt mt = meanTimes(inc);
            observedTotal += res.observedSeconds();
            availableTotal += res.availableSeconds();
            unavailableTotal += unavailable;
            rows.add(new ResourceAvailabilityRow(r.getId(), r.getName(), r.getType(), r.getType().getLabel(), r.getEnvironment(),
                    r.getEnvironment().getLabel(), r.getGroup() == null ? null : r.getGroup().getName(), r.getCurrentStatus(),
                    r.getCurrentStatus().getLabel(), res.percent(), res.observedSeconds(), res.availableSeconds(), unavailable,
                    maintenanceSeconds, lat == null ? 0 : lat.getTotal(), lat == null ? 0 : lat.getFailures(),
                    lat == null ? null : round(lat.getAvgResponseMs()), lat == null ? null : lat.getMaxResponseMs(),
                    lat == null ? null : round(lat.getP95ResponseMs()), inc.size(), mt.mttr(), mt.mtbf(), mt.mtbfNote()));
        }
        Mt overall = meanTimes(periodIncidents);
        Double overallPercent = observedTotal == 0 ? null : Math.round(availableTotal * 10000.0 / observedTotal) / 100.0;
        return new AvailabilityReport(from, to, period, mode == MaintenanceMode.EXCLUDED ? "excluded" : "counted",
                AVAILABILITY_FORMULA + (mode == MaintenanceMode.EXCLUDED
                        ? " Períodos em manutenção EXCLUÍDOS do tempo observado."
                        : " Períodos em manutenção CONTADOS como indisponibilidade."),
                rows, overallPercent, periodIncidents.size(), overall.mttr(), unavailableTotal);
    }

    @Transactional(readOnly = true)
    public IncidentsReport incidentsReport(OrganizationContext ctx, String period, UUID resourceId) {
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);
        List<Incident> list = incidents.findAll((root, q, cb) -> {
            var p = cb.and(cb.equal(root.get("organizationId"), ctx.organizationId()), cb.greaterThanOrEqualTo(root.get("openedAt"), from));
            return resourceId == null ? p : cb.and(p, cb.equal(root.get("resourceId"), resourceId));
        }).stream().sorted(Comparator.comparing(Incident::getOpenedAt).reversed()).toList();
        Map<UUID, String> names = resources.findByOrganizationIdOrderByNameAsc(ctx.organizationId()).stream()
                .collect(Collectors.toMap(MonitoredResource::getId, MonitoredResource::getName));
        List<IncidentRow> rows = list.stream().map(i -> toRow(i, names)).toList();
        Mt mt = meanTimes(list);
        Double mtta = average(list.stream().filter(i -> i.getAcknowledgedAt() != null)
                .mapToLong(i -> Duration.between(i.getOpenedAt(), i.getAcknowledgedAt()).getSeconds()).boxed().toList());
        return new IncidentsReport(from, to, period, rows, list.size(),
                list.stream().filter(i -> i.getOrigin() == Incident.Origin.AUTOMATIC).count(),
                list.stream().filter(i -> i.getOrigin() == Incident.Origin.MANUAL).count(),
                count(list, Severity.CRITICAL), count(list, Severity.HIGH), count(list, Severity.WARNING), count(list, Severity.INFO),
                mt.mttr(), mtta,
                "MTTR = média de (resolvido − aberto) dos incidentes resolvidos no período; MTTA = média de (reconhecido − aberto). "
                + (mt.mttr() == null ? "Sem incidentes resolvidos no período: MTTR indisponível." : ""));
    }

    @Transactional(readOnly = true)
    public ResourceReport resourceReport(OrganizationContext ctx, UUID resourceId, String period, String maintenance) {
        MonitoredResource r = resources.findByIdAndOrganizationId(resourceId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
        AvailabilityReport av = availability(ctx, period, maintenance, null);
        ResourceAvailabilityRow summary = av.rows().stream().filter(row -> row.resourceId().equals(resourceId)).findFirst().orElse(null);
        IncidentsReport ir = incidentsReport(ctx, period, resourceId);

        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);
        int bucket;
        String granularity;
        List<SeriesPoint> points;
        if (window.compareTo(Duration.ofHours(6)) <= 0) { bucket = 60; granularity = "raw"; points = samples.seriesFromSamples(resourceId, from, to, bucket); }
        else if (window.compareTo(Duration.ofDays(7)) <= 0) { bucket = 300; granularity = "rollup_5m"; points = rollups.series(resourceId, bucket, from, to); if (points.isEmpty()) { points = samples.seriesFromSamples(resourceId, from, to, bucket); granularity = "raw_fallback"; } }
        else { bucket = 3600; granularity = "rollup_1h"; points = rollups.series(resourceId, bucket, from, to); }
        List<MetricTrendPoint> trend = points.stream().map(p -> new MetricTrendPoint(p.getBucket(), dbl(p.getCpuAvg()), dbl(p.getCpuMax()),
                dbl(p.getMemPercentAvg()), dbl(p.getMemPercentMax()), dbl(p.getDiskPercentAvg()), dbl(p.getDiskPercentMax()))).toList();
        List<BucketStats> lat = executions.bucketStatsForResource(resourceId, ctx.organizationId(), from, to, Periods.bucketSeconds(window));
        List<LatencyPoint> latency = lat.stream().map(b -> new LatencyPoint(b.getBucket(), round(b.getAvgResponseMs()), b.getMaxResponseMs(),
                b.getTotal(), b.getTotal() - b.getSuccesses())).toList();
        return new ResourceReport(summary, ir.rows(), trend, granularity, latency);
    }

    // ------------------------------------------------------------------ helpers

    record Mt(Double mttr, Double mtbf, String mtbfNote) {}

    static Mt meanTimes(List<Incident> list) {
        List<Long> ttr = list.stream().filter(i -> i.getResolvedAt() != null)
                .map(i -> Duration.between(i.getOpenedAt(), i.getResolvedAt()).getSeconds()).toList();
        Double mttr = average(ttr);
        List<Incident> sorted = list.stream().sorted(Comparator.comparing(Incident::getOpenedAt)).toList();
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            Incident prev = sorted.get(i - 1);
            if (prev.getResolvedAt() != null && sorted.get(i).getOpenedAt().isAfter(prev.getResolvedAt())) {
                gaps.add(Duration.between(prev.getResolvedAt(), sorted.get(i).getOpenedAt()).getSeconds());
            }
        }
        Double mtbf = average(gaps);
        String note = list.size() < 2 ? "MTBF exige ao menos 2 incidentes no período" : gaps.isEmpty() ? "Sem intervalos mensuráveis entre incidentes" : null;
        return new Mt(mttr, mtbf, note);
    }

    static Double average(List<Long> values) {
        if (values.isEmpty()) return null;
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0) * 10) / 10.0;
    }

    private static long count(List<Incident> list, Severity s) {
        return list.stream().filter(i -> i.getSeverity() == s).count();
    }

    /** Segundos passados em um estado específico dentro da janela. */
    static long secondsIn(Instant from, Instant to, ResourceStatus atStart, List<ResourceStatusHistory> transitions, ResourceStatus target) {
        long total = 0;
        Instant cursor = from;
        ResourceStatus current = atStart == null ? ResourceStatus.PENDING : atStart;
        for (ResourceStatusHistory t : transitions) {
            Instant at = t.getChangedAt().isBefore(from) ? from : t.getChangedAt();
            if (at.isAfter(to)) break;
            if (current == target) total += Math.max(0, Duration.between(cursor, at).toSeconds());
            cursor = at;
            current = t.getToStatus();
        }
        if (current == target) total += Math.max(0, Duration.between(cursor, to).toSeconds());
        return total;
    }

    private IncidentRow toRow(Incident i, Map<UUID, String> names) {
        Instant end = i.getResolvedAt() == null ? Instant.now() : i.getResolvedAt();
        String assignee = i.getAssigneeId() == null ? null : users.findById(i.getAssigneeId()).map(User::getFullName).orElse(null);
        return new IncidentRow(i.getId(), i.getNumber(), i.getTitle(), i.getSeverity(), i.getSeverity().getLabel(), i.getStatus().name(),
                i.getStatus().getLabel(), i.getOrigin().name(), i.getOrigin().getLabel(), i.getResourceId(),
                i.getResourceId() == null ? null : names.getOrDefault(i.getResourceId(), "Recurso removido"), assignee,
                i.getOpenedAt(), i.getAcknowledgedAt(), i.getResolvedAt(), Duration.between(i.getOpenedAt(), end).getSeconds(),
                i.getAcknowledgedAt() == null ? null : Duration.between(i.getOpenedAt(), i.getAcknowledgedAt()).getSeconds(), i.getCause());
    }

    private static Double round(Double v) { return v == null ? null : Math.round(v * 10) / 10.0; }
    private static Double dbl(BigDecimal v) { return v == null ? null : v.doubleValue(); }
}
