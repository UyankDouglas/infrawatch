package com.infrawatch.integrations;

import com.infrawatch.alerts.AlertEnums.Severity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enfileira entregas externas (outbox) na mesma transação do fato gerador.
 * Uma entrega por canal elegível; {@code dedupeKey} impede duplicidades.
 */
@Service
public class DeliveryService {

    private final NotificationChannelRepository channels;
    private final NotificationDeliveryRepository deliveries;
    private final IntegrationsProperties properties;

    public DeliveryService(NotificationChannelRepository channels, NotificationDeliveryRepository deliveries,
                           IntegrationsProperties properties) {
        this.channels = channels;
        this.deliveries = deliveries;
        this.properties = properties;
    }

    @Transactional
    public int enqueue(UUID organizationId, String eventType, Severity severity, String title, String message, String link,
                       UUID resourceId, String dedupeKey) {
        List<NotificationChannel> eligible = channels.findByOrganizationIdAndEnabledTrue(organizationId).stream()
                .filter(c -> c.accepts(eventType, severity)).toList();
        int n = 0;
        for (NotificationChannel c : eligible) {
            String key = dedupeKey == null ? null : dedupeKey;
            if (key != null && deliveries.existsByChannelIdAndDedupeKey(c.getId(), key)) continue;
            String payload = WebhookPayload.json(organizationId, eventType, severity, title, message,
                    link == null ? null : properties.baseUrl() + link, resourceId, Instant.now());
            deliveries.save(new NotificationDelivery(c.getId(), organizationId, eventType, severity, title, message, link, resourceId, key, payload));
            n++;
        }
        return n;
    }
}
