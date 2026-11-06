package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Operator;
import com.infrawatch.alerts.AlertEnums.Severity;
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

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private Operator operator;

    @Column(precision = 12, scale = 2)
    private BigDecimal threshold;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private int occurrences = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AlertRule() {}

    public AlertRule(UUID organizationId, String name, Metric metric, Severity severity, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.metric = metric;
        this.severity = severity;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    /** Regra se aplica ao recurso? (recurso específico > grupo > toda a organização) */
    public boolean appliesTo(UUID resourceId, UUID resourceGroupId) {
        if (this.resourceId != null) return this.resourceId.equals(resourceId);
        if (this.groupId != null) return this.groupId.equals(resourceGroupId);
        return true;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Metric getMetric() { return metric; }
    public Operator getOperator() { return operator; }
    public BigDecimal getThreshold() { return threshold; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getOccurrences() { return occurrences; }
    public Severity getSeverity() { return severity; }
    public UUID getResourceId() { return resourceId; }
    public UUID getGroupId() { return groupId; }
    public boolean isEnabled() { return enabled; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setMetric(Metric metric) { this.metric = metric; }
    public void setOperator(Operator operator) { this.operator = operator; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
