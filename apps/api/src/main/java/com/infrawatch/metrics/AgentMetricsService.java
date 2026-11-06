package com.infrawatch.metrics;

import com.infrawatch.common.ApiException;
import com.infrawatch.metrics.MetricsDtos.AgentMetricsResponse;
import com.infrawatch.metrics.MetricsDtos.AgentResponse;
import com.infrawatch.metrics.MetricsDtos.LatestSample;
import com.infrawatch.metrics.MetricsDtos.MetricValue;
import com.infrawatch.metrics.MetricsDtos.SeriesPointDto;
import com.infrawatch.metrics.AgentRepository;
import com.infrawatch.metrics.MetricRollupRepository;
import com.infrawatch.metrics.MetricSampleRepository;
import com.infrawatch.metrics.SeriesPoint;
import com.infrawatch.monitoring.Periods;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.security.OrganizationContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura das métricas do agente: último valor por métrica (ou "indisponível") e séries por período. */
@Service
public class AgentMetricsService {

    private final MonitoredResourceRepository resources;
    private final AgentRepository agents;
    private final MetricSampleRepository samples;
    private final MetricRollupRepository rollups;
    private final AgentService agentService;
    private final MetricsProperties properties;

    public AgentMetricsService(MonitoredResourceRepository resources, AgentRepository agents,
                               MetricSampleRepository samples, MetricRollupRepository rollups,
                               AgentService agentService, MetricsProperties properties) {
        this.resources = resources;
        this.agents = agents;
        this.samples = samples;
        this.rollups = rollups;
        this.agentService = agentService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AgentMetricsResponse metrics(OrganizationContext ctx, UUID resourceId, String period) {
        MonitoredResource resource = resources.findByIdAndOrganizationId(resourceId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);

        Optional<Agent> agentOpt = agents.findByResourceId(resource.getId());
        if (agentOpt.isEmpty()) {
            return new AgentMetricsResponse(resource.getId(), "agent", false, null, false, from, to, 0, "none",
                    null, List.of(), List.of("cpu", "memory", "disk", "uptime"), List.of(),
                    "Nenhum agente registrado para este recurso. Métricas de CPU, memória e disco exigem um agente autorizado.");
        }
        Agent agent = agentOpt.get();
        boolean stale = agentService.isStale(agent, to);

        // Granularidade: brutas até 6 h; rollup de 5 min até 7 d; rollup de 1 h acima disso.
        int bucket;
        String granularity;
        List<SeriesPoint> points;
        if (window.compareTo(Duration.ofHours(6)) <= 0) {
            bucket = Math.max(agent.getIntervalSeconds(), window.toHours() <= 1 ? 30 : 60);
            granularity = "raw";
            points = samples.seriesFromSamples(resource.getId(), from, to, bucket);
        } else if (window.compareTo(Duration.ofDays(7)) <= 0) {
            bucket = 300;
            granularity = "rollup_5m";
            points = rollups.series(resource.getId(), bucket, from, to);
            if (points.isEmpty()) {
                // Rollups ainda não gerados (ex.: agente recém-instalado): usa brutas disponíveis.
                points = samples.seriesFromSamples(resource.getId(), from, to, bucket);
                granularity = "raw_fallback";
            }
        } else {
            bucket = 3600;
            granularity = "rollup_1h";
            points = rollups.series(resource.getId(), bucket, from, to);
        }

        LatestSample latest = samples.findFirstByResourceIdOrderBySampledAtDesc(resource.getId())
                .map(s -> new LatestSample(s.getSampledAt(), s.getReceivedAt(), s.getCpuPercent(), s.getLoadAvg1m(),
                        s.getMemTotalBytes(), s.getMemUsedBytes(), s.getMemPercent(), s.getDiskPath(),
                        s.getDiskTotalBytes(), s.getDiskUsedBytes(), s.getDiskPercent(), s.getUptimeSeconds()))
                .orElse(null);

        List<MetricValue> values = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        value(values, missing, "cpu", "Uso de CPU", latest == null ? null : latest.cpuPercent(), "%");
        value(values, missing, "memory", "Memória utilizada", latest == null ? null : latest.memPercent(), "%");
        value(values, missing, "disk", "Disco utilizado", latest == null ? null : latest.diskPercent(), "%");
        value(values, missing, "uptime", "Uptime do sistema",
                latest == null || latest.uptimeSeconds() == null ? null : BigDecimal.valueOf(latest.uptimeSeconds()), "s");
        value(values, missing, "load", "Carga média (1 min)", latest == null ? null : latest.loadAvg1m(), "");

        List<SeriesPointDto> series = points.stream().map(p -> new SeriesPointDto(p.getBucket(),
                p.getSamples() == null ? 0 : p.getSamples(), p.getCpuAvg(), p.getCpuMax(), p.getMemPercentAvg(),
                p.getMemPercentMax(), p.getMemUsedAvg(), p.getDiskPercentAvg(), p.getDiskPercentMax(), p.getDiskUsedAvg()))
                .toList();

        String note = stale
                ? "O agente não envia amostras há mais de " + (agent.getIntervalSeconds() * properties.staleAfterMultiplier())
                  + " s. Os valores exibidos são os últimos recebidos."
                : "Métricas coletadas pelo agente autorizado no próprio servidor (não derivadas de verificações HTTP).";

        AgentResponse agentResponse = agentService.toResponse(agent, resource.getName());
        return new AgentMetricsResponse(resource.getId(), "agent", true, agentResponse, stale, from, to, bucket,
                granularity, latest, values, missing, series, note);
    }

    private static void value(List<MetricValue> values, List<String> missing, String key, String label,
                              BigDecimal value, String unit) {
        boolean available = value != null;
        if (!available) missing.add(key);
        values.add(new MetricValue(key, label, value, unit, available,
                available ? null : "Métrica não reportada pelo agente"));
    }
}
