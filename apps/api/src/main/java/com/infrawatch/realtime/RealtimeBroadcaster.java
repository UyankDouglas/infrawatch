package com.infrawatch.realtime;

import com.infrawatch.common.events.DomainEvents.AlertChanged;
import com.infrawatch.common.events.DomainEvents.CheckCompleted;
import com.infrawatch.common.events.DomainEvents.NotificationCreated;
import com.infrawatch.common.events.DomainEvents.ResourceStatusChanged;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Retransmite eventos de domínio por WebSocket somente após o commit da transação de origem,
 * garantindo que o cliente nunca receba um evento cujo estado não esteja persistido.
 * Tópicos: {@code /topic/org/{orgId}} (organização) e {@code /user/queue/notifications} (usuário).
 */
@Component
public class RealtimeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RealtimeBroadcaster.class);

    private final SimpMessagingTemplate messaging;
    private final Map<UUID, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final Counter sent;

    public RealtimeBroadcaster(SimpMessagingTemplate messaging, MeterRegistry registry) {
        this.messaging = messaging;
        this.sent = Counter.builder("infrawatch.engine.realtime.events").register(registry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ResourceStatusChanged e) {
        publish(e.organizationId(), "resource.status_changed", e.at(), Map.of(
                "resourceId", e.resourceId(), "resourceName", e.resourceName(),
                "fromStatus", e.from(), "toStatus", e.to(), "reason", e.reason() == null ? "" : e.reason(),
                "source", e.source()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CheckCompleted e) {
        publish(e.organizationId(), "resource.check_completed", e.at(), Map.of(
                "resourceId", e.resourceId(), "resourceName", e.resourceName(), "success", e.success(),
                "httpStatus", e.httpStatus() == null ? -1 : e.httpStatus(),
                "responseTimeMs", e.responseTimeMs() == null ? -1 : e.responseTimeMs(),
                "errorCode", e.errorCode() == null ? "" : e.errorCode(), "status", e.status()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AlertChanged e) {
        publish(e.organizationId(), "alert." + e.kind(), Instant.now(), e.payload());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(NotificationCreated e) {
        RealtimeEvent event = envelope(e.organizationId(), "notification.created", Instant.now(), e.payload());
        try {
            messaging.convertAndSendToUser(e.userEmail(), "/queue/notifications", event);
            sent.increment();
        } catch (RuntimeException ex) {
            log.warn("Falha ao enviar notificação em tempo real: {}", ex.getMessage());
        }
    }

    private void publish(UUID orgId, String type, Instant at, Object payload) {
        RealtimeEvent event = envelope(orgId, type, at, payload);
        try {
            messaging.convertAndSend("/topic/org/" + orgId, event);
            sent.increment();
        } catch (RuntimeException ex) {
            log.warn("Falha ao publicar evento {}: {}", type, ex.getMessage());
        }
    }

    private RealtimeEvent envelope(UUID orgId, String type, Instant at, Object payload) {
        long seq = sequences.computeIfAbsent(orgId, k -> new AtomicLong()).incrementAndGet();
        return new RealtimeEvent(UUID.randomUUID().toString(), seq, type, orgId, at == null ? Instant.now() : at, payload);
    }
}
