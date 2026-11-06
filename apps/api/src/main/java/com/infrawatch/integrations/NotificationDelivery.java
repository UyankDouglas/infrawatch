package com.infrawatch.integrations;

import com.infrawatch.alerts.AlertEnums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Linha do outbox: uma entrega externa pendente/realizada para um canal. */
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    public enum Status { PENDING, SENT, FAILED, SKIPPED, EXPIRED }

    @Id private UUID id;
    @Column(name = "channel_id", nullable = false) private UUID channelId;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Severity severity;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 1000) private String message;
    @Column(length = 300) private String link;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(name = "dedupe_key", length = 200) private String dedupeKey;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "response_code") private Integer responseCode;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected NotificationDelivery() {}

    public NotificationDelivery(UUID channelId, UUID organizationId, String eventType, Severity severity, String title,
                                String message, String link, UUID resourceId, String dedupeKey, String payload) {
        this.id = UUID.randomUUID();
        this.channelId = channelId;
        this.organizationId = organizationId;
        this.eventType = eventType;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.link = link;
        this.resourceId = resourceId;
        this.dedupeKey = dedupeKey;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void sent(Instant at, Integer code) { status = Status.SENT; sentAt = at; responseCode = code; lastError = null; attempts++; }

    public void failed(Instant now, String error, Integer code, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(500, error.length()));
        responseCode = code;
        if (attempts >= maxAttempts) {
            status = Status.FAILED;
        } else {
            long backoff = (long) (30 * Math.pow(2, attempts - 1)); // 30 s, 60 s, 120 s, 240 s…
            nextAttemptAt = now.plusSeconds(Math.min(backoff, 3600));
        }
    }

    /** Reserva a entrega para o despachante atual: outros despachantes só a verão de novo após o prazo (lease). */
    public void lease(Instant until) { this.nextAttemptAt = until; }

    public void skipped(String why) { status = Status.SKIPPED; lastError = why; }
    public void expired() { status = Status.EXPIRED; }

    public UUID getId() { return id; }
    public UUID getChannelId() { return channelId; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEventType() { return eventType; }
    public Severity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getLink() { return link; }
    public UUID getResourceId() { return resourceId; }
    public String getDedupeKey() { return dedupeKey; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getSentAt() { return sentAt; }
    public Integer getResponseCode() { return responseCode; }
    public Instant getCreatedAt() { return createdAt; }
}
