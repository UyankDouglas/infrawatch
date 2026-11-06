package com.infrawatch.incidents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_comments")
public class IncidentComment {

    @Id private UUID id;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "user_id") private UUID userId;
    @Column(nullable = false, length = 4000) private String body;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected IncidentComment() {}

    public IncidentComment(UUID incidentId, UUID userId, String body) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.userId = userId;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getUserId() { return userId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
