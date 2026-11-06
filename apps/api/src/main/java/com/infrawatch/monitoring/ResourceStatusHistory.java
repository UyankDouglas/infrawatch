package com.infrawatch.monitoring;

import com.infrawatch.resource.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resource_status_history")
public class ResourceStatusHistory {

    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private ResourceStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ResourceStatus toStatus;

    @Column(length = 300)
    private String reason;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ResourceStatusHistory() {}

    public ResourceStatusHistory(UUID resourceId, UUID organizationId, ResourceStatus from, ResourceStatus to,
                                 String reason, UUID executionId, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.resourceId = resourceId;
        this.organizationId = organizationId;
        this.fromStatus = from;
        this.toStatus = to;
        this.reason = reason;
        this.executionId = executionId;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public UUID getOrganizationId() { return organizationId; }
    public ResourceStatus getFromStatus() { return fromStatus; }
    public ResourceStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public UUID getExecutionId() { return executionId; }
    public Instant getChangedAt() { return changedAt; }
}
