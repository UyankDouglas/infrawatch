package com.infrawatch.notifications;

import com.infrawatch.alerts.AlertEnums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type {
        RESOURCE_DOWN("Recurso indisponível"),
        RESOURCE_RECOVERED("Recurso recuperado"),
        RESOURCE_NO_DATA("Recurso sem dados"),
        ALERT_TRIGGERED("Alerta disparado"),
        ALERT_RESOLVED("Alerta resolvido"),
        INCIDENT_CREATED("Incidente criado"),
        INCIDENT_ASSIGNED("Incidente atribuído"),
        MAINTENANCE_SCHEDULED("Manutenção agendada");

        private final String label;
        Type(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(length = 300)
    private String link;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "alert_event_id")
    private UUID alertEventId;

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(UUID organizationId, UUID userId, Type type, Severity severity, String title, String message,
                        String link, UUID resourceId, UUID alertEventId, String dedupeKey) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.userId = userId;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.link = link;
        this.resourceId = resourceId;
        this.alertEventId = alertEventId;
        this.dedupeKey = dedupeKey;
        this.createdAt = Instant.now();
    }

    public void markRead(Instant at) { if (readAt == null) readAt = at; }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getUserId() { return userId; }
    public Type getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getLink() { return link; }
    public UUID getResourceId() { return resourceId; }
    public UUID getAlertEventId() { return alertEventId; }
    public String getDedupeKey() { return dedupeKey; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
