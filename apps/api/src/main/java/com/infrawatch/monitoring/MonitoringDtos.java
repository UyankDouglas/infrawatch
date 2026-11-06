package com.infrawatch.monitoring;

import com.infrawatch.resource.ResourceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MonitoringDtos {
    private MonitoringDtos() {}

    public record ExecutionResponse(UUID id, UUID runId, String checkType, Instant startedAt, Instant finishedAt,
                                    int durationMs, boolean success, Integer httpStatus, Integer responseTimeMs,
                                    String errorCode, String errorMessage, String resolvedAddress) {
        public static ExecutionResponse of(HealthCheckExecution e) {
            return new ExecutionResponse(e.getId(), e.getRunId(), e.getCheckType(), e.getStartedAt(), e.getFinishedAt(),
                    e.getDurationMs(), e.isSuccess(), e.getHttpStatus(), e.getResponseTimeMs(), e.getErrorCode(),
                    e.getErrorMessage(), e.getResolvedAddress());
        }
    }

    public record HistoryResponse(UUID id, ResourceStatus fromStatus, String fromLabel, ResourceStatus toStatus,
                                  String toLabel, String reason, UUID executionId, Instant changedAt) {
        public static HistoryResponse of(ResourceStatusHistory h) {
            return new HistoryResponse(h.getId(), h.getFromStatus(), h.getFromStatus().getLabel(), h.getToStatus(),
                    h.getToStatus().getLabel(), h.getReason(), h.getExecutionId(), h.getChangedAt());
        }
    }

    public record LatencyPoint(Instant bucket, long total, long successes, Double avgResponseMs, Integer maxResponseMs,
                               Double successRate) {}

    public record ResourceMetricsResponse(UUID resourceId, Instant from, Instant to, int bucketSeconds,
                                          List<LatencyPoint> latency, Double availabilityPercent,
                                          long observedSeconds, long availableSeconds,
                                          String availabilityNote) {}

    public record RunNowResponse(boolean executed, ExecutionResponse execution, ResourceStatus previousStatus,
                                 ResourceStatus currentStatus, boolean transitioned, String message) {}
}
