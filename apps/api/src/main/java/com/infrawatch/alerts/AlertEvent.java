package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Resolution;
import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.alerts.AlertEnums.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Um alerta disparado por uma regra para um recurso. Permanece único enquanto não for resolvido. */
@Entity
@Table(name = "alert_events")
public class AlertEvent {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(precision = 12, scale = 2)
    private BigDecimal value;

    @Column(precision = 12, scale = 2)
    private BigDecimal threshold;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    @Column(name = "acknowledged_at") private Instant acknowledgedAt;
    @Column(name = "acknowledged_by") private UUID acknowledgedBy;
    @Column(name = "acknowledge_note", length = 500) private String acknowledgeNote;

    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by") private UUID resolvedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Resolution resolution;

    @Column(name = "resolution_note", length = 500) private String resolutionNote;

    @Column(name = "occurrence_count", nullable = false) private int occurrenceCount = 1;
    @Column(name = "notification_count", nullable = false) private int notificationCount;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AlertEvent() {}

    public AlertEvent(UUID organizationId, UUID ruleId, UUID resourceId, Metric metric, Severity severity,
                      String title, String message, BigDecimal value, BigDecimal threshold, Instant at) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.ruleId = ruleId;
        this.resourceId = resourceId;
        this.metric = metric;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.value = value;
        this.threshold = threshold;
        this.triggeredAt = at;
        this.lastEvaluatedAt = at;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void reevaluated(BigDecimal value, Instant at) {
        this.value = value;
        this.lastEvaluatedAt = at;
        this.occurrenceCount++;
    }

    public void acknowledge(UUID userId, String note, Instant at) {
        if (status == Status.OPEN) {
            this.status = Status.ACKNOWLEDGED;
            this.acknowledgedAt = at;
            this.acknowledgedBy = userId;
            this.acknowledgeNote = note;
        }
    }

    public void resolve(Resolution resolution, UUID userId, String note, Instant at) {
        if (status != Status.RESOLVED) {
            this.status = Status.RESOLVED;
            this.resolvedAt = at;
            this.resolvedBy = userId;
            this.resolution = resolution;
            this.resolutionNote = note;
        }
    }

    public void notified() { this.notificationCount++; }

    public boolean isActive() { return status != Status.RESOLVED; }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getRuleId() { return ruleId; }
    public UUID getResourceId() { return resourceId; }
    public Metric getMetric() { return metric; }
    public Severity getSeverity() { return severity; }
    public Status getStatus() { return status; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public BigDecimal getValue() { return value; }
    public BigDecimal getThreshold() { return threshold; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public String getAcknowledgeNote() { return acknowledgeNote; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
    public Resolution getResolution() { return resolution; }
    public String getResolutionNote() { return resolutionNote; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public int getNotificationCount() { return notificationCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
