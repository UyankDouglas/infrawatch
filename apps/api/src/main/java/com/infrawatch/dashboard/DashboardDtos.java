package com.infrawatch.dashboard;

import com.infrawatch.resource.Environment;
import com.infrawatch.resource.ResourceStatus;
import com.infrawatch.resource.ResourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record StatusCount(ResourceStatus status, String label, long total) {}

    public record TypeCount(ResourceType type, String label, long total) {}

    public record ResourceSummary(UUID id, String name, ResourceType type, String typeLabel, Environment environment,
                                  String environmentLabel, ResourceStatus status, String statusLabel,
                                  Instant statusSince, Instant lastCheckAt, Integer lastResponseTimeMs,
                                  String lastErrorCode, String groupName) {}

    public record TransitionSummary(UUID id, UUID resourceId, String resourceName, ResourceStatus fromStatus,
                                    String fromLabel, ResourceStatus toStatus, String toLabel, String reason,
                                    Instant changedAt) {}

    public record Overview(
            Instant from, Instant to, String period,
            long totalResources, long available, long unavailable, long degraded, long withoutData,
            long pendingResources, long unknownResources, long pausedResources,
            long activeIncidents, long activeAlerts,
            Double availabilityPercent, long observedSeconds, String availabilityNote,
            Double averageResponseMs, long checksInPeriod, long failedChecksInPeriod,
            List<StatusCount> statusDistribution, List<TypeCount> resourcesByType,
            List<ResourceSummary> problematic, List<ResourceSummary> resources,
            List<TransitionSummary> recentTransitions) {}

    public record AvailabilityPoint(Instant bucket, long total, long successes, Double successRate) {}

    public record AvailabilitySeries(Instant from, Instant to, int bucketSeconds, List<AvailabilityPoint> points,
                                     String note) {}

    public record LatencySeriesPoint(Instant bucket, Double avgResponseMs, Integer maxResponseMs) {}

    public record LatencySeries(UUID resourceId, String resourceName, Double averageMs, List<LatencySeriesPoint> points) {}

    public record LatencyResponse(Instant from, Instant to, int bucketSeconds, List<LatencySeries> series) {}
}
