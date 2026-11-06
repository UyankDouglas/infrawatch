package com.infrawatch.dashboard;

import com.infrawatch.dashboard.DashboardDtos.AvailabilityPoint;
import com.infrawatch.dashboard.DashboardDtos.AvailabilitySeries;
import com.infrawatch.dashboard.DashboardDtos.LatencyResponse;
import com.infrawatch.dashboard.DashboardDtos.LatencySeries;
import com.infrawatch.dashboard.DashboardDtos.LatencySeriesPoint;
import com.infrawatch.dashboard.DashboardDtos.Overview;
import com.infrawatch.dashboard.DashboardDtos.ResourceSummary;
import com.infrawatch.dashboard.DashboardDtos.StatusCount;
import com.infrawatch.dashboard.DashboardDtos.TransitionSummary;
import com.infrawatch.dashboard.DashboardDtos.TypeCount;
import com.infrawatch.monitoring.AvailabilityCalculator;
import com.infrawatch.monitoring.HealthCheckExecutionRepository;
import com.infrawatch.monitoring.HealthCheckExecutionRepository.BucketStats;
import com.infrawatch.monitoring.HealthCheckExecutionRepository.ResourceAverage;
import com.infrawatch.monitoring.HealthCheckExecutionRepository.ResourceBucketStats;
import com.infrawatch.monitoring.Periods;
import com.infrawatch.monitoring.ResourceStatusHistory;
import com.infrawatch.monitoring.ResourceStatusHistoryRepository;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import com.infrawatch.resource.ResourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.infrawatch.alerts.AlertEventRepository;
import com.infrawatch.alerts.AlertMapper;
import com.infrawatch.alerts.AlertEnums;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final MonitoredResourceRepository resources;
    private final HealthCheckExecutionRepository executions;
    private final ResourceStatusHistoryRepository history;
    private final AlertEventRepository alerts;
    private final AlertMapper alertMapper;

    public DashboardService(MonitoredResourceRepository resources, HealthCheckExecutionRepository executions,
                            ResourceStatusHistoryRepository history, AlertEventRepository alerts,
                            AlertMapper alertMapper) {
        this.resources = resources;
        this.executions = executions;
        this.history = history;
        this.alerts = alerts;
        this.alertMapper = alertMapper;
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID orgId, String period) {
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);

        List<MonitoredResource> all = resources.findByOrganizationIdOrderByNameAsc(orgId);
        Map<ResourceStatus, Long> byStatus = new EnumMap<>(ResourceStatus.class);
        for (ResourceStatus s : ResourceStatus.values()) byStatus.put(s, 0L);
        long paused = 0;
        for (MonitoredResource r : all) {
            byStatus.merge(r.getCurrentStatus(), 1L, Long::sum);
            if (!r.isEnabled()) paused++;
        }

        List<StatusCount> distribution = byStatus.entrySet().stream()
                .map(e -> new StatusCount(e.getKey(), e.getKey().getLabel(), e.getValue())).toList();

        Map<ResourceType, Long> byType = new EnumMap<>(ResourceType.class);
        for (MonitoredResource r : all) byType.merge(r.getType(), 1L, Long::sum);
        List<TypeCount> types = byType.entrySet().stream()
                .map(e -> new TypeCount(e.getKey(), e.getKey().getLabel(), e.getValue())).toList();

        // Disponibilidade agregada (tempo observado com estado conhecido, somado entre os recursos).
        List<ResourceStatusHistory> transitions = history
                .findByOrganizationIdAndChangedAtGreaterThanEqualOrderByChangedAtAsc(orgId, from);
        Map<UUID, List<ResourceStatusHistory>> byResource = transitions.stream()
                .collect(Collectors.groupingBy(ResourceStatusHistory::getResourceId));
        long observed = 0, availableSeconds = 0;
        for (MonitoredResource r : all) {
            List<ResourceStatusHistory> own = byResource.getOrDefault(r.getId(), List.of());
            Instant start = r.getCreatedAt().isAfter(from) ? r.getCreatedAt() : from;
            ResourceStatus atStart = own.isEmpty() ? r.getCurrentStatus() : own.getFirst().getFromStatus();
            AvailabilityCalculator.Result res = AvailabilityCalculator.compute(start, to, atStart, own);
            observed += res.observedSeconds();
            availableSeconds += res.availableSeconds();
        }
        Double availability = observed == 0 ? null : Math.round(availableSeconds * 10000.0 / observed) / 100.0;

        List<BucketStats> buckets = executions.bucketStatsForOrganization(orgId, from, to, (int) window.toSeconds());
        long checks = buckets.stream().mapToLong(BucketStats::getTotal).sum();
        long okChecks = buckets.stream().mapToLong(BucketStats::getSuccesses).sum();
        Double avgResponse = executions.averageResponseTime(orgId, from, to);

        List<ResourceSummary> problematic = all.stream()
                .filter(r -> r.getCurrentStatus() == ResourceStatus.DOWN || r.getCurrentStatus() == ResourceStatus.DEGRADED)
                .sorted(Comparator.comparing((MonitoredResource r) -> r.getCurrentStatus() == ResourceStatus.DOWN ? 0 : 1)
                        .thenComparing(MonitoredResource::getStatusSince, Comparator.reverseOrder()))
                .map(DashboardService::summary).toList();

        Map<UUID, String> names = all.stream().collect(Collectors.toMap(MonitoredResource::getId, MonitoredResource::getName));
        List<TransitionSummary> recent = history.findTop20ByOrganizationIdOrderByChangedAtDesc(orgId).stream()
                .map(h -> new TransitionSummary(h.getId(), h.getResourceId(), names.getOrDefault(h.getResourceId(), "Recurso removido"),
                        h.getFromStatus(), h.getFromStatus().getLabel(), h.getToStatus(), h.getToStatus().getLabel(),
                        h.getReason(), h.getChangedAt()))
                .toList();

        return new Overview(from, to, period,
                all.size(), byStatus.get(ResourceStatus.UP), byStatus.get(ResourceStatus.DOWN),
                byStatus.get(ResourceStatus.DEGRADED),
                byStatus.get(ResourceStatus.PENDING) + byStatus.get(ResourceStatus.UNKNOWN),
                byStatus.get(ResourceStatus.PENDING), byStatus.get(ResourceStatus.UNKNOWN), paused,
                0, alerts.countByOrganizationIdAndStatusIn(orgId, List.of(AlertEnums.Status.OPEN, AlertEnums.Status.ACKNOWLEDGED)),
                availability, observed,
                "Tempo disponível ÷ tempo observado com estado conhecido, somado entre os recursos. Períodos aguardando/sem dados são excluídos. Manutenções: Fase 4.",
                avgResponse == null ? null : Math.round(avgResponse * 10) / 10.0, checks, checks - okChecks,
                distribution, types, problematic,
                all.stream().map(DashboardService::summary).toList(), recent,
                alerts.findTop10ByOrganizationIdOrderByTriggeredAtDesc(orgId).stream().map(alertMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public AvailabilitySeries availability(UUID orgId, String period) {
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);
        int bucket = Periods.bucketSeconds(window);
        List<AvailabilityPoint> points = executions.bucketStatsForOrganization(orgId, from, to, bucket).stream()
                .map(b -> new AvailabilityPoint(b.getBucket(), b.getTotal(), b.getSuccesses(),
                        b.getTotal() == 0 ? null : Math.round(b.getSuccesses() * 10000.0 / b.getTotal()) / 100.0))
                .toList();
        return new AvailabilitySeries(from, to, bucket, points,
                "Percentual de verificações bem-sucedidas por intervalo (todas as verificações da organização).");
    }

    @Transactional(readOnly = true)
    public LatencyResponse latency(UUID orgId, String period, int limit) {
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);
        int bucket = Periods.bucketSeconds(window);

        Map<UUID, String> names = resources.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.toMap(MonitoredResource::getId, MonitoredResource::getName));
        List<ResourceAverage> averages = executions.averagePerResource(orgId, from, to);
        List<UUID> selected = averages.stream()
                .sorted(Comparator.comparing(ResourceAverage::getAvgResponseMs, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.clamp(limit, 1, 10))
                .map(ResourceAverage::getResourceId).toList();
        Map<UUID, Double> avgById = averages.stream()
                .collect(Collectors.toMap(ResourceAverage::getResourceId, a -> a.getAvgResponseMs() == null ? 0 : a.getAvgResponseMs()));

        Map<UUID, List<LatencySeriesPoint>> seriesPoints = new LinkedHashMap<>();
        for (UUID id : selected) seriesPoints.put(id, new ArrayList<>());
        for (ResourceBucketStats s : executions.bucketStatsPerResource(orgId, from, to, bucket)) {
            List<LatencySeriesPoint> list = seriesPoints.get(s.getResourceId());
            if (list != null) {
                list.add(new LatencySeriesPoint(s.getBucket(),
                        s.getAvgResponseMs() == null ? null : Math.round(s.getAvgResponseMs() * 10) / 10.0,
                        s.getMaxResponseMs()));
            }
        }
        List<LatencySeries> series = seriesPoints.entrySet().stream()
                .map(e -> new LatencySeries(e.getKey(), names.getOrDefault(e.getKey(), "Recurso removido"),
                        Math.round(avgById.getOrDefault(e.getKey(), 0.0) * 10) / 10.0, e.getValue()))
                .toList();
        return new LatencyResponse(from, to, bucket, series);
    }

    private static ResourceSummary summary(MonitoredResource r) {
        return new ResourceSummary(r.getId(), r.getName(), r.getType(), r.getType().getLabel(), r.getEnvironment(),
                r.getEnvironment().getLabel(), r.getCurrentStatus(), r.getCurrentStatus().getLabel(), r.getStatusSince(),
                r.getLastCheckAt(), r.getLastResponseTimeMs(), r.getLastErrorCode(),
                r.getGroup() == null ? null : r.getGroup().getName());
    }

    @SuppressWarnings("unused")
    private static <T, K> Map<K, T> index(List<T> items, Function<T, K> key) {
        return items.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
