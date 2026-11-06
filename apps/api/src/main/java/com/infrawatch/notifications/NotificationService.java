package com.infrawatch.notifications;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.common.events.DomainEvents.NotificationCreated;
import com.infrawatch.organization.OrganizationMember;
import com.infrawatch.organization.OrganizationMemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificações internas. Criadas na mesma transação do fato que as originou (padrão outbox simplificado:
 * a linha persistida é a fonte de verdade; o envio em tempo real é um complemento pós-commit).
 * {@code dedupeKey} impede duplicidades por usuário quando o mesmo fato é processado mais de uma vez.
 * Canais externos (e-mail, webhook) consumirão esta tabela na Fase 5.
 */
@Service
public class NotificationService {

    public record NotificationDto(UUID id, Notification.Type type, String typeLabel, Severity severity,
                                  String severityLabel, String title, String message, String link,
                                  UUID resourceId, UUID alertEventId, Instant readAt, Instant createdAt) {
        public static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getType().getLabel(), n.getSeverity(),
                    n.getSeverity().getLabel(), n.getTitle(), n.getMessage(), n.getLink(), n.getResourceId(),
                    n.getAlertEventId(), n.getReadAt(), n.getCreatedAt());
        }
    }

    private final NotificationRepository notifications;
    private final OrganizationMemberRepository members;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository notifications, OrganizationMemberRepository members,
                               ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.members = members;
        this.events = events;
    }

    /** Envia a um único usuário (ex.: atribuição de incidente). */
    @Transactional
    public boolean notifyUser(UUID organizationId, UUID userId, Notification.Type type, Severity severity, String title,
                              String message, String link, UUID resourceId, UUID alertEventId, String dedupeKey) {
        if (dedupeKey != null && notifications.existsByUserIdAndDedupeKey(userId, dedupeKey)) return false;
        String email = members.findByUserIdAndOrganizationId(userId, organizationId)
                .map(m -> m.getUser().getEmail()).orElse(null);
        if (email == null) return false;
        Notification n = notifications.save(new Notification(organizationId, userId, type, severity, title, message, link,
                resourceId, alertEventId, dedupeKey));
        events.publishEvent(new NotificationCreated(organizationId, userId, email, NotificationDto.of(n)));
        return true;
    }

    /** Envia a todos os membros da organização. Retorna quantas notificações foram criadas. */
    @Transactional
    public int notifyOrganization(UUID organizationId, Notification.Type type, Severity severity, String title,
                                  String message, String link, UUID resourceId, UUID alertEventId, String dedupeKey) {
        List<OrganizationMember> recipients = members.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
        int created = 0;
        for (OrganizationMember m : recipients) {
            UUID userId = m.getUser().getId();
            if (dedupeKey != null && notifications.existsByUserIdAndDedupeKey(userId, dedupeKey)) continue;
            Notification n = notifications.save(new Notification(organizationId, userId, type, severity, title,
                    message, link, resourceId, alertEventId, dedupeKey));
            events.publishEvent(new NotificationCreated(organizationId, userId, m.getUser().getEmail(),
                    NotificationDto.of(n)));
            created++;
        }
        return created;
    }
}
