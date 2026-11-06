package com.infrawatch.incidents;

import com.infrawatch.alerts.AlertEnums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {

    public enum Status {
        OPEN("Aberto"), ACKNOWLEDGED("Reconhecido"), INVESTIGATING("Em investigação"),
        RESOLVED("Resolvido"), CLOSED("Encerrado");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
        public boolean isActive() { return this != RESOLVED && this != CLOSED; }
    }

    public enum Origin {
        AUTOMATIC("Automático"), MANUAL("Manual");

        private final String label;
        Origin(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(name = "alert_event_id") private UUID alertEventId;
    @Column(nullable = false) private long number;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 4000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Severity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.OPEN;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Origin origin;
    @Column(name = "assignee_id") private UUID assigneeId;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(length = 2000) private String cause;
    @Column(length = 4000) private String notes;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Incident() {}

    public Incident(UUID organizationId, long number, String title, Severity severity, Origin origin, UUID resourceId,
                    UUID alertEventId, UUID createdBy, Instant openedAt) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.number = number;
        this.title = title;
        this.severity = severity;
        this.origin = origin;
        this.resourceId = resourceId;
        this.alertEventId = alertEventId;
        this.createdBy = createdBy;
        this.openedAt = openedAt;
    }

    @PrePersist void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    /** Aplica a transição de status registrando os horários relevantes. */
    public void transition(Status next, Instant at) {
        this.status = next;
        switch (next) {
            case ACKNOWLEDGED -> { if (acknowledgedAt == null) acknowledgedAt = at; }
            case RESOLVED -> { resolvedAt = at; closedAt = null; }
            case CLOSED -> { if (resolvedAt == null) resolvedAt = at; closedAt = at; }
            case OPEN, INVESTIGATING -> { resolvedAt = null; closedAt = null; }
        }
    }

    public Long durationSeconds(Instant now) {
        Instant end = resolvedAt != null ? resolvedAt : now;
        return java.time.Duration.between(openedAt, end).getSeconds();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getResourceId() { return resourceId; }
    public UUID getAlertEventId() { return alertEventId; }
    public long getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Severity getSeverity() { return severity; }
    public Status getStatus() { return status; }
    public Origin getOrigin() { return origin; }
    public UUID getAssigneeId() { return assigneeId; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public String getCause() { return cause; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setAssigneeId(UUID assigneeId) { this.assigneeId = assigneeId; }
    public void setCause(String cause) { this.cause = cause; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setAlertEventId(UUID alertEventId) { this.alertEventId = alertEventId; }
}
