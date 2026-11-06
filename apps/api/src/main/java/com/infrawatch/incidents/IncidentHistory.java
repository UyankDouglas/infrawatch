package com.infrawatch.incidents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro imutável de cada alteração de um incidente (quem, quando, de → para). */
@Entity
@Table(name = "incident_history")
public class IncidentHistory {

    public enum EventType {
        CREATED, STATUS_CHANGED, ASSIGNED, UNASSIGNED, SEVERITY_CHANGED, CAUSE_SET, NOTES_UPDATED,
        TITLE_UPDATED, DESCRIPTION_UPDATED, COMMENTED, RESOURCE_RECOVERED, AUTO_RESOLVED, LINKED_ALERT
    }

    @Id private UUID id;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "from_value", length = 200) private String fromValue;
    @Column(name = "to_value", length = 200) private String toValue;
    @Column(length = 1000) private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected IncidentHistory() {}

    public IncidentHistory(UUID incidentId, UUID userId, EventType type, String from, String to, String details, Instant at) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.userId = userId;
        this.eventType = type.name();
        this.fromValue = from;
        this.toValue = to;
        this.details = details;
        this.createdAt = at;
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getUserId() { return userId; }
    public String getEventType() { return eventType; }
    public String getFromValue() { return fromValue; }
    public String getToValue() { return toValue; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
