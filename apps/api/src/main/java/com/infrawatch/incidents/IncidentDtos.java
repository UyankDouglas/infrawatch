package com.infrawatch.incidents;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.incidents.Incident.Origin;
import com.infrawatch.incidents.Incident.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IncidentDtos {
    private IncidentDtos() {}

    public record CreateRequest(
            @NotBlank(message = "Informe o título.") @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull(message = "Informe a severidade.") Severity severity,
            UUID resourceId,
            UUID assigneeId) {}

    public record PatchRequest(
            @Size(min = 1, max = 200) String title,
            @Size(max = 4000) String description,
            Severity severity,
            UUID assigneeId,
            Boolean clearAssignee,
            @Size(max = 2000) String cause,
            @Size(max = 4000) String notes) {}

    public record StatusRequest(@NotNull(message = "Informe o status.") Status status, @Size(max = 1000) String note) {}

    public record CommentRequest(@NotBlank(message = "Escreva um comentário.") @Size(max = 4000) String body) {}

    public record UserRef(UUID id, String name) {}

    public record IncidentResponse(UUID id, long number, String title, String description, Severity severity,
                                   String severityLabel, Status status, String statusLabel, Origin origin,
                                   String originLabel, UUID resourceId, String resourceName, UUID alertEventId,
                                   UserRef assignee, UserRef createdBy, Instant openedAt, Instant acknowledgedAt,
                                   Instant resolvedAt, Instant closedAt, Long durationSeconds, String cause,
                                   String notes, long commentCount, Instant updatedAt) {}

    /** Item unificado da timeline: histórico, comentários e transições do recurso durante o incidente. */
    public record TimelineItem(String kind, Instant at, UserRef user, String eventType, String fromValue,
                               String toValue, String text) {}

    public record IncidentDetail(IncidentResponse incident, List<TimelineItem> timeline, List<Status> allowedTransitions) {}

    public record IncidentSummary(long open, long acknowledged, long investigating, long resolvedLast24h,
                                  long critical, long high, Double mttrSeconds, String mttrNote) {}
}
