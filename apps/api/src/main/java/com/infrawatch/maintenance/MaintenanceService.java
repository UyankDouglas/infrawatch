package com.infrawatch.maintenance;

import com.infrawatch.alerts.AlertEnums;
import com.infrawatch.alerts.AlertEvent;
import com.infrawatch.alerts.AlertEventRepository;
import com.infrawatch.audit.AuditService;
import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.common.ApiException;
import com.infrawatch.common.events.DomainEvents.AlertChanged;
import com.infrawatch.common.events.DomainEvents.MaintenanceChanged;
import com.infrawatch.common.events.DomainEvents.ResourceStatusChanged;
import com.infrawatch.maintenance.MaintenanceDtos.CreateRequest;
import com.infrawatch.maintenance.MaintenanceDtos.PatchRequest;
import com.infrawatch.maintenance.MaintenanceDtos.ResourceRef;
import com.infrawatch.maintenance.MaintenanceDtos.UserRef;
import com.infrawatch.maintenance.MaintenanceDtos.WindowResponse;
import com.infrawatch.maintenance.MaintenanceWindow.FinishReason;
import com.infrawatch.maintenance.MaintenanceWindow.Status;
import com.infrawatch.monitoring.ResourceStatusHistory;
import com.infrawatch.monitoring.ResourceStatusHistoryRepository;
import com.infrawatch.notifications.Notification;
import com.infrawatch.notifications.NotificationService;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.organization.OrganizationMemberRepository;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import com.infrawatch.security.OrganizationContext;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manutenções programadas. Durante a janela os recursos ficam em EM MANUTENÇÃO: verificações continuam
 * (registram latência), mas nenhuma transição, alerta ou incidente é gerado. Ao encerrar, o recurso volta
 * a AGUARDANDO VERIFICAÇÃO e o estado é recalculado pela próxima coleta, sem herdar falhas da janela.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final MaintenanceWindowRepository windows;
    private final MonitoredResourceRepository resources;
    private final ResourceStatusHistoryRepository statusHistory;
    private final AlertEventRepository alerts;
    private final UserRepository users;
    private final OrganizationMemberRepository members;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;

    public MaintenanceService(MaintenanceWindowRepository windows, MonitoredResourceRepository resources,
                              ResourceStatusHistoryRepository statusHistory, AlertEventRepository alerts,
                              UserRepository users, OrganizationMemberRepository members,
                              NotificationService notifications, AuditService audit, ApplicationEventPublisher publisher) {
        this.windows = windows;
        this.resources = resources;
        this.statusHistory = statusHistory;
        this.alerts = alerts;
        this.users = users;
        this.members = members;
        this.notifications = notifications;
        this.audit = audit;
        this.publisher = publisher;
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional(readOnly = true)
    public List<WindowResponse> list(OrganizationContext ctx) {
        return windows.findByOrganizationIdOrderByStartsAtDesc(ctx.organizationId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WindowResponse> upcoming(OrganizationContext ctx) {
        return windows.findUpcomingOrActive(ctx.organizationId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WindowResponse> forResource(OrganizationContext ctx, UUID resourceId) {
        resources.findByIdAndOrganizationId(resourceId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
        return windows.findUpcomingOrActiveForResource(resourceId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WindowResponse get(OrganizationContext ctx, UUID id) {
        return toResponse(require(ctx, id));
    }

    @Transactional
    public WindowResponse create(OrganizationContext ctx, CreateRequest req) {
        ctx.require(MemberRole.OPERATOR);
        validatePeriod(req.startsAt(), req.endsAt());
        Set<UUID> ids = validateResources(ctx, req.resourceIds());
        UUID responsible = validateResponsible(ctx, req.responsibleId());
        MaintenanceWindow window = windows.save(new MaintenanceWindow(ctx.organizationId(), req.title().trim(),
                blank(req.reason()), req.startsAt(), req.endsAt(), ids, responsible, ctx.userId()));
        notifications.notifyOrganization(ctx.organizationId(), Notification.Type.MAINTENANCE_SCHEDULED,
                AlertEnums.Severity.INFO, "Manutenção agendada: " + window.getTitle(),
                ids.size() + " recurso(s) — de " + req.startsAt() + " a " + req.endsAt(),
                "/manutencoes", null, null, "maintenance_scheduled:" + window.getId());
        audit.record(ctx.organizationId(), ctx.userId(), "maintenance.scheduled", "maintenance_window", window.getId(),
                "Manutenção \"" + window.getTitle() + "\" agendada para " + ids.size() + " recurso(s)");
        WindowResponse response = toResponse(window);
        publisher.publishEvent(new MaintenanceChanged(ctx.organizationId(), "scheduled", response));
        // Janela com início no passado: ativa imediatamente.
        if (!req.startsAt().isAfter(Instant.now())) {
            activate(window, Instant.now());
            response = toResponse(window);
        }
        return response;
    }

    @Transactional
    public WindowResponse patch(OrganizationContext ctx, UUID id, PatchRequest req) {
        ctx.require(MemberRole.OPERATOR);
        MaintenanceWindow w = require(ctx, id);
        if (w.getStatus() != Status.SCHEDULED && w.getStatus() != Status.ACTIVE) {
            throw ApiException.conflict("Somente manutenções agendadas ou em andamento podem ser editadas.");
        }
        if (req.title() != null) w.setTitle(req.title().trim());
        if (req.reason() != null) w.setReason(blank(req.reason()));
        if (req.responsibleId() != null) w.setResponsibleId(validateResponsible(ctx, req.responsibleId()));
        Instant starts = req.startsAt() != null ? req.startsAt() : w.getStartsAt();
        Instant ends = req.endsAt() != null ? req.endsAt() : w.getEndsAt();
        if (w.getStatus() == Status.ACTIVE && req.startsAt() != null) {
            throw ApiException.conflict("O início de uma manutenção em andamento não pode ser alterado.");
        }
        validatePeriod(starts, ends);
        w.setStartsAt(starts);
        w.setEndsAt(ends);
        if (req.resourceIds() != null) {
            if (w.getStatus() == Status.ACTIVE) {
                throw ApiException.conflict("Os recursos de uma manutenção em andamento não podem ser alterados; encerre-a e agende outra.");
            }
            w.setResourceIds(validateResources(ctx, req.resourceIds()));
        }
        audit.record(ctx.organizationId(), ctx.userId(), "maintenance.updated", "maintenance_window", id,
                "Manutenção \"" + w.getTitle() + "\" atualizada");
        WindowResponse response = toResponse(w);
        publisher.publishEvent(new MaintenanceChanged(ctx.organizationId(), "updated", response));
        return response;
    }

    /** Encerramento antecipado (janela em andamento) ou cancelamento (janela agendada). */
    @Transactional
    public WindowResponse finishEarly(OrganizationContext ctx, UUID id) {
        ctx.require(MemberRole.OPERATOR);
        MaintenanceWindow w = require(ctx, id);
        Instant now = Instant.now();
        if (w.getStatus() == Status.SCHEDULED) {
            w.finish(FinishReason.CANCELLED, now);
            audit.record(ctx.organizationId(), ctx.userId(), "maintenance.cancelled", "maintenance_window", id,
                    "Manutenção \"" + w.getTitle() + "\" cancelada");
            WindowResponse response = toResponse(w);
            publisher.publishEvent(new MaintenanceChanged(ctx.organizationId(), "cancelled", response));
            return response;
        }
        if (w.getStatus() != Status.ACTIVE) {
            throw ApiException.conflict("Esta manutenção já foi encerrada.");
        }
        complete(w, FinishReason.ENDED_EARLY, now);
        audit.record(ctx.organizationId(), ctx.userId(), "maintenance.ended_early", "maintenance_window", id,
                "Manutenção \"" + w.getTitle() + "\" encerrada antecipadamente");
        return toResponse(w);
    }

    // ------------------------------------------------------------------ ciclo de vida (agendador)

    @Transactional
    public int activateDue(Instant now) {
        int n = 0;
        for (MaintenanceWindow w : windows.findByStatusAndStartsAtLessThanEqual(Status.SCHEDULED, now)) {
            activate(w, now);
            n++;
        }
        return n;
    }

    @Transactional
    public int completeDue(Instant now) {
        int n = 0;
        for (MaintenanceWindow w : windows.findByStatusAndEndsAtLessThanEqual(Status.ACTIVE, now)) {
            complete(w, FinishReason.ENDED_ON_SCHEDULE, now);
            n++;
        }
        return n;
    }

    private void activate(MaintenanceWindow w, Instant now) {
        w.activate(now);
        for (UUID resourceId : w.getResourceIds()) {
            resources.lockById(resourceId).ifPresent(resource -> enterMaintenance(resource, w, now));
        }
        log.info("Manutenção \"{}\" iniciada ({} recurso(s))", w.getTitle(), w.getResourceIds().size());
        publisher.publishEvent(new MaintenanceChanged(w.getOrganizationId(), "started", toResponse(w)));
    }

    private void enterMaintenance(MonitoredResource resource, MaintenanceWindow w, Instant now) {
        ResourceStatus previous = resource.getCurrentStatus();
        if (previous == ResourceStatus.MAINTENANCE) return;
        resource.setStatusBeforeMaintenance(previous);
        resource.applyStatus(ResourceStatus.MAINTENANCE, now);
        resource.resetCounters();
        statusHistory.save(new ResourceStatusHistory(resource.getId(), resource.getOrganizationId(), previous,
                ResourceStatus.MAINTENANCE, "Manutenção programada: " + w.getTitle(), null, now));
        // Alertas ativos do recurso são resolvidos: a janela é planejada, não uma falha.
        for (AlertEvent alert : alerts.findActiveByResource(resource.getId())) {
            alert.resolve(AlertEnums.Resolution.MAINTENANCE, null, "Janela de manutenção iniciada: " + w.getTitle(), now);
            publisher.publishEvent(new AlertChanged(resource.getOrganizationId(), "resolved",
                    java.util.Map.of("id", alert.getId(), "status", "RESOLVED", "resolution", "MAINTENANCE")));
        }
        publisher.publishEvent(new ResourceStatusChanged(resource.getOrganizationId(), resource.getId(), resource.getName(),
                previous, ResourceStatus.MAINTENANCE, "Manutenção programada: " + w.getTitle(), now, "maintenance"));
    }

    private void complete(MaintenanceWindow w, FinishReason why, Instant now) {
        w.finish(why, now);
        for (UUID resourceId : w.getResourceIds()) {
            resources.lockById(resourceId).ifPresent(resource -> {
                boolean stillCovered = windows.findActiveForResource(resourceId).stream().anyMatch(o -> !o.getId().equals(w.getId()));
                if (stillCovered || resource.getCurrentStatus() != ResourceStatus.MAINTENANCE) return;
                exitMaintenance(resource, w, now);
            });
        }
        log.info("Manutenção \"{}\" encerrada ({})", w.getTitle(), why);
        publisher.publishEvent(new MaintenanceChanged(w.getOrganizationId(), "finished", toResponse(w)));
    }

    private void exitMaintenance(MonitoredResource resource, MaintenanceWindow w, Instant now) {
        resource.setStatusBeforeMaintenance(null);
        resource.applyStatus(ResourceStatus.PENDING, now);
        resource.resetCounters();
        resource.setNextCheckAt(now);
        statusHistory.save(new ResourceStatusHistory(resource.getId(), resource.getOrganizationId(), ResourceStatus.MAINTENANCE,
                ResourceStatus.PENDING, "Manutenção encerrada; aguardando nova verificação", null, now));
        publisher.publishEvent(new ResourceStatusChanged(resource.getOrganizationId(), resource.getId(), resource.getName(),
                ResourceStatus.MAINTENANCE, ResourceStatus.PENDING, "Manutenção encerrada: " + w.getTitle(), now, "maintenance"));
    }

    // ------------------------------------------------------------------ helpers

    private static void validatePeriod(Instant starts, Instant ends) {
        if (starts == null || ends == null || !ends.isAfter(starts)) {
            throw ApiException.badRequest("maintenance_period", "O término deve ser posterior ao início.");
        }
        if (Duration.between(starts, ends).toDays() > 30) {
            throw ApiException.badRequest("maintenance_period", "Uma janela não pode durar mais de 30 dias.");
        }
    }

    private Set<UUID> validateResources(OrganizationContext ctx, List<UUID> ids) {
        Set<UUID> result = new LinkedHashSet<>();
        for (UUID id : ids) {
            resources.findByIdAndOrganizationId(id, ctx.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Recurso não encontrado: " + id));
            result.add(id);
        }
        return result;
    }

    private UUID validateResponsible(OrganizationContext ctx, UUID userId) {
        if (userId == null) return null;
        members.findByUserIdAndOrganizationId(userId, ctx.organizationId())
                .orElseThrow(() -> ApiException.badRequest("responsible_not_member", "O responsável precisa ser membro da organização."));
        return userId;
    }

    private MaintenanceWindow require(OrganizationContext ctx, UUID id) {
        return windows.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Manutenção não encontrada."));
    }

    public WindowResponse toResponse(MaintenanceWindow w) {
        List<ResourceRef> refs = w.getResourceIds().stream()
                .map(id -> new ResourceRef(id, resources.findById(id).map(MonitoredResource::getName).orElse("Recurso removido")))
                .toList();
        Instant end = w.getFinishedAt() != null ? w.getFinishedAt() : w.getEndsAt();
        return new WindowResponse(w.getId(), w.getTitle(), w.getReason(), w.getStartsAt(), w.getEndsAt(), w.getStatus(),
                w.getStatus().getLabel(), refs, userRef(w.getResponsibleId()), userRef(w.getCreatedBy()), w.getActivatedAt(),
                w.getFinishedAt(), w.getFinishReason(), Duration.between(w.getStartsAt(), end).getSeconds(), w.getCreatedAt());
    }

    private UserRef userRef(UUID id) {
        return id == null ? null : new UserRef(id, users.findById(id).map(User::getFullName).orElse("Usuário removido"));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
