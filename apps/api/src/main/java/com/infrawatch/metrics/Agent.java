package com.infrawatch.metrics;

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

/** Agente autorizado associado a um recurso do tipo servidor. Só envia métricas; nunca recebe comandos. */
@Entity
@Table(name = "agents")
public class Agent {

    public enum Status { ACTIVE, REVOKED }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "resource_id", nullable = false, unique = true)
    private UUID resourceId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds = 30;

    @Column(length = 255)
    private String hostname;

    @Column(name = "agent_version", length = 40)
    private String agentVersion;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_sample_at")
    private Instant lastSampleAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Agent() {}

    public Agent(UUID organizationId, UUID resourceId, String name, int intervalSeconds, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.resourceId = resourceId;
        this.name = name;
        this.intervalSeconds = intervalSeconds;
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

    public void touch(Instant seenAt, Instant sampledAt, String hostname, String version) {
        this.lastSeenAt = seenAt;
        if (sampledAt != null && (lastSampleAt == null || sampledAt.isAfter(lastSampleAt))) this.lastSampleAt = sampledAt;
        if (hostname != null) this.hostname = hostname;
        if (version != null) this.agentVersion = version;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getResourceId() { return resourceId; }
    public String getName() { return name; }
    public Status getStatus() { return status; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public String getHostname() { return hostname; }
    public String getAgentVersion() { return agentVersion; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getLastSampleAt() { return lastSampleAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(Status status) { this.status = status; }
    public void setName(String name) { this.name = name; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
}
