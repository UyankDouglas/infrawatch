package com.infrawatch.reports;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.resource.Environment;
import com.infrawatch.resource.ResourceStatus;
import com.infrawatch.resource.ResourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {}

    public record ResourceAvailabilityRow(
            UUID resourceId, String name, ResourceType type, String typeLabel, Environment environment, String environmentLabel,
            String groupName, ResourceStatus currentStatus, String currentStatusLabel,
            Double availabilityPercent, long observedSeconds, long availableSeconds, long unavailableSeconds, long maintenanceSeconds,
            long checks, long failedChecks, Double avgResponseMs, Integer maxResponseMs, Double p95ResponseMs,
            long incidents, Double mttrSeconds, Double mtbfSeconds, String mtbfNote) {}

    public record AvailabilityReport(Instant from, Instant to, String period, String maintenanceMode, String formula,
                                     List<ResourceAvailabilityRow> rows, Double overallAvailabilityPercent,
                                     long totalIncidents, Double overallMttrSeconds, long totalUnavailableSeconds) {}

    public record IncidentRow(UUID id, long number, String title, Severity severity, String severityLabel, String status,
                              String statusLabel, String origin, String originLabel, UUID resourceId, String resourceName,
                              String assignee, Instant openedAt, Instant acknowledgedAt, Instant resolvedAt, Long durationSeconds,
                              Long timeToAcknowledgeSeconds, String cause) {}

    public record IncidentsReport(Instant from, Instant to, String period, List<IncidentRow> rows, long total,
                                  long automatic, long manual, long bySeverityCritical, long bySeverityHigh, long bySeverityWarning,
                                  long bySeverityInfo, Double mttrSeconds, Double mttaSeconds, String note) {}

    public record MetricTrendPoint(Instant bucket, Double cpuAvg, Double cpuMax, Double memAvg, Double memMax, Double diskAvg, Double diskMax) {}

    public record ResourceReport(ResourceAvailabilityRow summary, List<IncidentRow> incidents, List<MetricTrendPoint> metricTrend,
                                 String metricGranularity, List<LatencyPoint> latency) {}

    public record LatencyPoint(Instant bucket, Double avgResponseMs, Integer maxResponseMs, long total, long failures) {}

    public record ExportRequest(String type, String format, String period, String maintenance, UUID groupId) {}

    public record ExportResponse(UUID id, String type, String typeLabel, String format, String period, String maintenanceMode,
                                 String fileName, String contentType, long sizeBytes, int rowCount, String createdBy, Instant createdAt) {}
}
