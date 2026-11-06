package com.infrawatch.metrics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MetricsDtos {
    private MetricsDtos() {}

    // ----- gestão de agentes -----

    public record RegisterAgentRequest(
            @NotNull(message = "Informe o recurso.") UUID resourceId,
            @NotBlank(message = "Informe o nome do agente.") @Size(max = 120) String name,
            @Min(value = 10, message = "Intervalo mínimo de 10 s.") @Max(3600) Integer intervalSeconds) {}

    public record AgentResponse(UUID id, UUID resourceId, String resourceName, String name, Agent.Status status,
                                int intervalSeconds, String hostname, String agentVersion, Instant lastSeenAt,
                                Instant lastSampleAt, boolean stale, String tokenPrefix, Instant createdAt) {}

    /** Devolvido uma única vez: o token em claro não é armazenado. */
    public record AgentTokenResponse(AgentResponse agent, String token, String message) {}

    // ----- ingestão -----

    public record MemoryPayload(@PositiveOrZero Long totalBytes, @PositiveOrZero Long usedBytes) {}

    public record DiskPayload(@Size(max = 255) String path, @PositiveOrZero Long totalBytes, @PositiveOrZero Long usedBytes) {}

    public record SamplePayload(
            Instant sampledAt,
            @DecimalMin("0") @DecimalMax("100") BigDecimal cpuPercent,
            @DecimalMin("0") BigDecimal loadAvg1m,
            @Valid MemoryPayload memory,
            @Valid DiskPayload disk,
            @PositiveOrZero Long uptimeSeconds,
            @Size(max = 255) String hostname,
            @Size(max = 40) String agentVersion) {}

    public record IngestResponse(UUID sampleResourceId, Instant acceptedAt, int nextIntervalSeconds,
                                 List<String> ignoredMetrics) {}

    // ----- leitura -----

    public record MetricValue(String key, String label, BigDecimal value, String unit, boolean available,
                              String note) {}

    public record LatestSample(Instant sampledAt, Instant receivedAt, BigDecimal cpuPercent, BigDecimal loadAvg1m,
                               Long memTotalBytes, Long memUsedBytes, BigDecimal memPercent, String diskPath,
                               Long diskTotalBytes, Long diskUsedBytes, BigDecimal diskPercent, Long uptimeSeconds) {}

    public record SeriesPointDto(Instant bucket, int samples, BigDecimal cpuAvg, BigDecimal cpuMax,
                                 BigDecimal memPercentAvg, BigDecimal memPercentMax, Long memUsedAvg,
                                 BigDecimal diskPercentAvg, BigDecimal diskPercentMax, Long diskUsedAvg) {}

    public record AgentMetricsResponse(
            UUID resourceId, String source, boolean hasAgent, AgentResponse agent, boolean stale,
            Instant from, Instant to, int bucketSeconds, String granularity,
            LatestSample latest, List<MetricValue> values, List<String> missingMetrics,
            List<SeriesPointDto> series, String note) {}
}
