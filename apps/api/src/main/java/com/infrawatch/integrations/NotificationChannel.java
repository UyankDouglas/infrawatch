package com.infrawatch.integrations;

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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Canal externo de notificação (e-mail ou webhook) previamente cadastrado e autorizado. */
@Entity
@Table(name = "notification_channels")
public class NotificationChannel {

    public enum Type { EMAIL, WEBHOOK }

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Type type;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "email_recipients", length = 1000) private String emailRecipients;
    @Column(name = "webhook_url", length = 2048) private String webhookUrl;
    @Column(name = "webhook_secret", length = 200) private String webhookSecret;
    @Enumerated(EnumType.STRING) @Column(name = "min_severity", nullable = false, length = 20) private Severity minSeverity = Severity.INFO;
    @Column(name = "event_types", length = 500) private String eventTypes;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "last_delivery_at") private Instant lastDeliveryAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected NotificationChannel() {}

    public NotificationChannel(UUID organizationId, String name, Type type, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.type = type;
        this.createdBy = createdBy;
    }

    @PrePersist void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    /** Tipos de evento aceitos (vazio = todos). */
    public Set<String> eventTypeSet() {
        if (eventTypes == null || eventTypes.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.stream(eventTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    public boolean accepts(String eventType, Severity severity) {
        if (!enabled) return false;
        if (severity.getRank() < minSeverity.getRank()) return false;
        Set<String> types = eventTypeSet();
        return types.isEmpty() || types.contains(eventType);
    }

    public void recordDelivery(Instant at, String error) { this.lastDeliveryAt = at; this.lastError = error; }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public boolean isEnabled() { return enabled; }
    public String getEmailRecipients() { return emailRecipients; }
    public String getWebhookUrl() { return webhookUrl; }
    public String getWebhookSecret() { return webhookSecret; }
    public Severity getMinSeverity() { return minSeverity; }
    public String getEventTypes() { return eventTypes; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getLastDeliveryAt() { return lastDeliveryAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setEmailRecipients(String emailRecipients) { this.emailRecipients = emailRecipients; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public void setMinSeverity(Severity minSeverity) { this.minSeverity = minSeverity; }
    public void setEventTypes(String eventTypes) { this.eventTypes = eventTypes; }
}
