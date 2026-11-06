package com.infrawatch.maintenance;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "maintenance_windows")
public class MaintenanceWindow {

    public enum Status {
        SCHEDULED("Agendada"), ACTIVE("Em andamento"), COMPLETED("Concluída"), CANCELLED("Cancelada");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum FinishReason { ENDED_ON_SCHEDULE, ENDED_EARLY, CANCELLED }

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 2000) private String reason;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.SCHEDULED;
    @Column(name = "responsible_id") private UUID responsibleId;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Enumerated(EnumType.STRING) @Column(name = "finish_reason", length = 30) private FinishReason finishReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "maintenance_window_resources", joinColumns = @JoinColumn(name = "window_id"))
    @Column(name = "resource_id", nullable = false)
    private Set<UUID> resourceIds = new LinkedHashSet<>();

    protected MaintenanceWindow() {}

    public MaintenanceWindow(UUID organizationId, String title, String reason, Instant startsAt, Instant endsAt,
                             Set<UUID> resourceIds, UUID responsibleId, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.title = title;
        this.reason = reason;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.resourceIds = new LinkedHashSet<>(resourceIds);
        this.responsibleId = responsibleId;
        this.createdBy = createdBy;
    }

    @PrePersist void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public void activate(Instant at) { this.status = Status.ACTIVE; this.activatedAt = at; }

    public void finish(FinishReason why, Instant at) {
        this.status = why == FinishReason.CANCELLED ? Status.CANCELLED : Status.COMPLETED;
        this.finishReason = why;
        this.finishedAt = at;
        if (why == FinishReason.ENDED_EARLY) this.endsAt = at;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getTitle() { return title; }
    public String getReason() { return reason; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Status getStatus() { return status; }
    public UUID getResponsibleId() { return responsibleId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public FinishReason getFinishReason() { return finishReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<UUID> getResourceIds() { return resourceIds; }

    public void setTitle(String title) { this.title = title; }
    public void setReason(String reason) { this.reason = reason; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setResponsibleId(UUID responsibleId) { this.responsibleId = responsibleId; }
    public void setResourceIds(Set<UUID> ids) { this.resourceIds = new LinkedHashSet<>(ids); }
}
