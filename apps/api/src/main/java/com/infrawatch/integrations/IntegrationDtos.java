package com.infrawatch.integrations;

import com.infrawatch.alerts.AlertEnums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IntegrationDtos {
    private IntegrationDtos() {}

    public record ChannelRequest(
            @NotBlank(message = "Informe o nome do canal.") @Size(max = 120) String name,
            @NotNull(message = "Informe o tipo.") NotificationChannel.Type type,
            Boolean enabled,
            @Size(max = 20) List<@Size(max = 255) String> emailRecipients,
            @Size(max = 2048) String webhookUrl,
            @Size(max = 200) String webhookSecret,
            Severity minSeverity,
            @Size(max = 20) List<String> eventTypes) {}

    public record ChannelResponse(UUID id, String name, NotificationChannel.Type type, boolean enabled, List<String> emailRecipients,
                                  String webhookUrl, boolean hasSecret, Severity minSeverity, String minSeverityLabel,
                                  List<String> eventTypes, Instant lastDeliveryAt, String lastError, Instant createdAt,
                                  boolean dispatchEnabled) {}

    public record DeliveryResponse(UUID id, UUID channelId, String channelName, String eventType, Severity severity, String title,
                                   NotificationDelivery.Status status, int attempts, Instant nextAttemptAt, String lastError,
                                   Instant sentAt, Integer responseCode, Instant createdAt) {
        public static DeliveryResponse of(NotificationDelivery d, String channelName) {
            return new DeliveryResponse(d.getId(), d.getChannelId(), channelName, d.getEventType(), d.getSeverity(), d.getTitle(),
                    d.getStatus(), d.getAttempts(), d.getNextAttemptAt(), d.getLastError(), d.getSentAt(), d.getResponseCode(), d.getCreatedAt());
        }
    }
}
