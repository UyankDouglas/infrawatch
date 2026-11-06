package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Operator;
import com.infrawatch.alerts.AlertEnums.Resolution;
import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.alerts.AlertEnums.Status;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AlertDtos {
    private AlertDtos() {}

    public record RuleRequest(
            @NotBlank(message = "Informe o nome da regra.") @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull(message = "Informe a métrica.") Metric metric,
            Operator operator,
            BigDecimal threshold,
            @Min(0) @Max(86400) Integer durationSeconds,
            @Min(1) @Max(100) Integer occurrences,
            @NotNull(message = "Informe a severidade.") Severity severity,
            UUID resourceId,
            UUID groupId,
            Boolean enabled) {}

    public record RulePatch(
            @Size(min = 1, max = 120) String name,
            @Size(max = 500) String description,
            Operator operator,
            BigDecimal threshold,
            @Min(0) @Max(86400) Integer durationSeconds,
            @Min(1) @Max(100) Integer occurrences,
            Severity severity,
            UUID resourceId,
            UUID groupId,
            Boolean clearScope,
            Boolean enabled) {}

    public record RuleResponse(UUID id, String name, String description, Metric metric, String metricLabel,
                               Operator operator, BigDecimal threshold, String unit, int durationSeconds,
                               int occurrences, Severity severity, String severityLabel, UUID resourceId,
                               String resourceName, UUID groupId, String groupName, boolean enabled,
                               long activeAlerts, Instant createdAt, Instant updatedAt) {}

    public record AlertResponse(UUID id, UUID ruleId, String ruleName, UUID resourceId, String resourceName,
                                Metric metric, String metricLabel, Severity severity, String severityLabel,
                                Status status, String statusLabel, String title, String message, BigDecimal value,
                                BigDecimal threshold, String unit, Instant triggeredAt, Instant lastEvaluatedAt,
                                Instant acknowledgedAt, UUID acknowledgedBy, String acknowledgedByName,
                                String acknowledgeNote, Instant resolvedAt, UUID resolvedBy, String resolvedByName,
                                Resolution resolution, String resolutionNote, int occurrenceCount,
                                int notificationCount, Long durationSeconds) {}

    public record NoteRequest(@Size(max = 500) String note) {}

    public record AlertSummary(long open, long acknowledged, long critical, long high, long warning, long info) {}
}
