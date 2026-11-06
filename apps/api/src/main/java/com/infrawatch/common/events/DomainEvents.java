package com.infrawatch.common.events;

import com.infrawatch.resource.ResourceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Eventos de domínio publicados via ApplicationEventPublisher dentro da transação de origem.
 * O motor de alertas os consome de forma síncrona (mesma transação); o broadcaster WebSocket
 * só os retransmite após o commit.
 */
public final class DomainEvents {
    private DomainEvents() {}

    public record ResourceStatusChanged(UUID organizationId, UUID resourceId, String resourceName,
                                        ResourceStatus from, ResourceStatus to, String reason, Instant at,
                                        String source) {}

    public record CheckCompleted(UUID organizationId, UUID resourceId, String resourceName, boolean success,
                                 Integer httpStatus, Integer responseTimeMs, String errorCode,
                                 ResourceStatus status, Instant at) {}

    public record AgentSampleReceived(UUID organizationId, UUID resourceId, String resourceName,
                                      BigDecimal cpuPercent, BigDecimal memPercent, BigDecimal diskPercent,
                                      Instant at) {}

    /** kind: triggered | acknowledged | resolved | updated */
    public record AlertChanged(UUID organizationId, String kind, Object payload) {}

    public record NotificationCreated(UUID organizationId, UUID userId, String userEmail, Object payload) {}

    /** kind: created | updated | resolved */
    public record IncidentChanged(UUID organizationId, String kind, Object payload) {}

    /** kind: scheduled | updated | started | finished | cancelled */
    public record MaintenanceChanged(UUID organizationId, String kind, Object payload) {}
}
