package com.infrawatch.incidents;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.audit.AuditService;
import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.common.ApiException;
import com.infrawatch.common.events.DomainEvents.IncidentChanged;
import com.infrawatch.incidents.Incident.Origin;
import com.infrawatch.incidents.Incident.Status;
import com.infrawatch.incidents.IncidentDtos.CommentRequest;
import com.infrawatch.incidents.IncidentDtos.CreateRequest;
import com.infrawatch.incidents.IncidentDtos.IncidentDetail;
import com.infrawatch.incidents.IncidentDtos.IncidentResponse;
import com.infrawatch.incidents.IncidentDtos.IncidentSummary;
import com.infrawatch.incidents.IncidentDtos.PatchRequest;
import com.infrawatch.incidents.IncidentDtos.StatusRequest;
import com.infrawatch.incidents.IncidentDtos.TimelineItem;
import com.infrawatch.incidents.IncidentDtos.UserRef;
import com.infrawatch.incidents.IncidentHistory.EventType;
import com.infrawatch.monitoring.ResourceStatusHistory;
import com.infrawatch.monitoring.ResourceStatusHistoryRepository;
import com.infrawatch.notifications.Notification;
import com.infrawatch.notifications.NotificationService;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.organization.OrganizationMemberRepository;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.security.OrganizationContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    public record Filters(Status status, Boolean active, Severity severity, Origin origin, UUID resourceId, UUID assigneeId) {}

    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.OPEN, EnumSet.of(Status.ACKNOWLEDGED, Status.INVESTIGATING, Status.RESOLVED),
            Status.ACKNOWLEDGED, EnumSet.of(Status.INVESTIGATING, Status.RESOLVED),
            Status.INVESTIGATING, EnumSet.of(Status.RESOLVED),
            Status.RESOLVED, EnumSet.of(Status.CLOSED, Status.INVESTIGATING),
            Status.CLOSED, EnumSet.noneOf(Status.class));

    private final IncidentRepository incidents;
    private final IncidentCommentRepository comments;
    private final IncidentHistoryRepository history;
    private final ResourceStatusHistoryRepository statusHistory;
    private final MonitoredResourceRepository resources;
    private final UserRepository users;
    private final OrganizationMemberRepository members;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;

    public IncidentService(IncidentRepository incidents, IncidentCommentRepository comments,
                           IncidentHistoryRepository history, ResourceStatusHistoryRepository statusHistory,
                           MonitoredResourceRepository resources, UserRepository users,
                           OrganizationMemberRepository members, NotificationService notifications,
                           AuditService audit, ApplicationEventPublisher publisher) {
        this.incidents = incidents;
        this.comments = comments;
        this.history = history;
        this.statusHistory = statusHistory;
        this.resources = resources;
        this.users = users;
        this.members = members;
        this.notifications = notifications;
        this.audit = audit;
        this.publisher = publisher;
    }

    // ------------------------------------------------------------------ criação

    @Transactional
    public IncidentResponse createManual(OrganizationContext ctx, CreateRequest req) {
        ctx.require(MemberRole.OPERATOR);
        UUID resourceId = null;
        if (req.resourceId() != null) {
            resourceId = resources.findByIdAndOrganizationId(req.resourceId(), ctx.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Recurso não encontrado.")).getId();
        }
        Instant now = Instant.now();
        Incident incident = new Incident(ctx.organizationId(), incidents.maxNumber(ctx.organizationId()) + 1,
                req.title().trim(), req.severity(), Origin.MANUAL, resourceId, null, ctx.userId(), now);
        incident.setDescription(blank(req.description()));
        incident = incidents.save(incident);
        history.save(new IncidentHistory(incident.getId(), ctx.userId(), EventType.CREATED, null, Status.OPEN.name(),
                "Incidente aberto manualmente", now));
        if (req.assigneeId() != null) {
            assign(ctx, incident, req.assigneeId(), now);
        }
        notifications.notifyOrganization(ctx.organizationId(), Notification.Type.INCIDENT_CREATED, req.severity(),
                "Incidente #" + incident.getNumber() + ": " + incident.getTitle(), blank(req.description()),
                "/incidentes/" + incident.getId(), resourceId, null, "incident_created:" + incident.getId());
        audit.record(ctx.organizationId(), ctx.userId(), "incident.created", "incident", incident.getId(),
                "Incidente #" + incident.getNumber() + " \"" + incident.getTitle() + "\" aberto manualmente");
        IncidentResponse response = toResponse(incident);
        publisher.publishEvent(new IncidentChanged(ctx.organizationId(), "created", response));
        return response;
    }

    /** Abertura automática por transição de estado (chamado pelo IncidentEngine). */
    @Transactional
    public Incident openAutomatic(MonitoredResource resource, UUID alertEventId, String reason, Instant at) {
        Incident incident = new Incident(resource.getOrganizationId(), incidents.maxNumber(resource.getOrganizationId()) + 1,
                "Indisponibilidade: " + resource.getName(), Severity.CRITICAL, Origin.AUTOMATIC, resource.getId(),
                alertEventId, null, at);
        incident.setDescription(reason);
        incident = incidents.save(incident);
        history.save(new IncidentHistory(incident.getId(), null, EventType.CREATED, null, Status.OPEN.name(),
                "Aberto automaticamente: " + reason, at));
        if (alertEventId != null) {
            history.save(new IncidentHistory(incident.getId(), null, EventType.LINKED_ALERT, null, alertEventId.toString(),
                    "Vinculado ao alerta de indisponibilidade", at));
        }
        notifications.notifyOrganization(resource.getOrganizationId(), Notification.Type.INCIDENT_CREATED, Severity.CRITICAL,
                "Incidente #" + incident.getNumber() + ": " + incident.getTitle(), reason,
                "/incidentes/" + incident.getId(), resource.getId(), alertEventId, "incident_created:" + incident.getId());
        publisher.publishEvent(new IncidentChanged(resource.getOrganizationId(), "created", toResponse(incident)));
        return incident;
    }

    @Transactional(readOnly = true)
    public boolean findActiveAutomaticExists(UUID resourceId) {
        return incidents.findActiveAutomatic(resourceId).isPresent();
    }

    /** Resolução automática na recuperação (somente incidentes automáticos). */
    @Transactional
    public void autoResolve(MonitoredResource resource, String reason, Instant at) {
        incidents.findActiveAutomatic(resource.getId()).ifPresent(incident -> {
            Status previous = incident.getStatus();
            incident.transition(Status.RESOLVED, at);
            if (incident.getCause() == null) incident.setCause("Recuperação detectada: " + reason);
            history.save(new IncidentHistory(incident.getId(), null, EventType.AUTO_RESOLVED, previous.name(),
                    Status.RESOLVED.name(), reason, at));
            publisher.publishEvent(new IncidentChanged(resource.getOrganizationId(), "resolved", toResponse(incident)));
        });
    }

    /** Registra na timeline dos incidentes ativos do recurso uma transição de estado ocorrida durante o incidente. */
    @Transactional
    public void recordResourceTransition(UUID resourceId, String from, String to, String reason, Instant at) {
        for (Incident incident : incidents.findActiveByResource(resourceId)) {
            history.save(new IncidentHistory(incident.getId(), null, EventType.RESOURCE_RECOVERED, from, to, reason, at));
        }
    }

    // ------------------------------------------------------------------ gestão

    @Transactional
    public IncidentResponse patch(OrganizationContext ctx, UUID id, PatchRequest req) {
        ctx.require(MemberRole.OPERATOR);
        Incident incident = require(ctx, id);
        Instant now = Instant.now();
        if (req.title() != null && !req.title().trim().equals(incident.getTitle())) {
            history.save(new IncidentHistory(id, ctx.userId(), EventType.TITLE_UPDATED, incident.getTitle(), req.title().trim(), null, now));
            incident.setTitle(req.title().trim());
        }
        if (req.description() != null && !Objects.equals(blank(req.description()), incident.getDescription())) {
            history.save(new IncidentHistory(id, ctx.userId(), EventType.DESCRIPTION_UPDATED, null, null, "Descrição atualizada", now));
            incident.setDescription(blank(req.description()));
        }
        if (req.severity() != null && req.severity() != incident.getSeverity()) {
            history.save(new IncidentHistory(id, ctx.userId(), EventType.SEVERITY_CHANGED, incident.getSeverity().name(), req.severity().name(), null, now));
            incident.setSeverity(req.severity());
        }
        if (Boolean.TRUE.equals(req.clearAssignee()) && incident.getAssigneeId() != null) {
            history.save(new IncidentHistory(id, ctx.userId(), EventType.UNASSIGNED, userName(incident.getAssigneeId()), null, null, now));
            incident.setAssigneeId(null);
        } else if (req.assigneeId() != null && !req.assigneeId().equals(incident.getAssigneeId())) {
            assign(ctx, incident, req.assigneeId(), now);
        }
        if (req.cause() != null && !Objects.equals(blank(req.cause()), incident.getCause())) {
            history.save(new IncidentHistory(id, ctx.userId(), EventType.CAUSE_SET, null, null, blank(req.cause()), now));
            incident.setCause(blank(req.cause()));
        }
        if (req.notes() != null && !Objects.equals(blank(req.notes()), incident.getNotes())) {
            history.save(new IncidentHistory(id, ctx.userId(), EventType.NOTES_UPDATED, null, null, "Observações atualizadas", now));
            incident.setNotes(blank(req.notes()));
        }
        audit.record(ctx.organizationId(), ctx.userId(), "incident.updated", "incident", id, "Incidente #" + incident.getNumber() + " atualizado");
        IncidentResponse response = toResponse(incident);
        publisher.publishEvent(new IncidentChanged(ctx.organizationId(), "updated", response));
        return response;
    }

    @Transactional
    public IncidentResponse changeStatus(OrganizationContext ctx, UUID id, StatusRequest req) {
        ctx.require(MemberRole.OPERATOR);
        Incident incident = require(ctx, id);
        Status from = incident.getStatus();
        if (!TRANSITIONS.get(from).contains(req.status())) {
            throw ApiException.conflict("Transição de " + from.getLabel() + " para " + req.status().getLabel() + " não é permitida.");
        }
        Instant now = Instant.now();
        incident.transition(req.status(), now);
        history.save(new IncidentHistory(id, ctx.userId(), EventType.STATUS_CHANGED, from.name(), req.status().name(), blank(req.note()), now));
        audit.record(ctx.organizationId(), ctx.userId(), "incident.status_changed", "incident", id,
                "Incidente #" + incident.getNumber() + ": " + from.getLabel() + " → " + req.status().getLabel());
        IncidentResponse response = toResponse(incident);
        String kind = req.status() == Status.RESOLVED ? "resolved" : "updated";
        publisher.publishEvent(new IncidentChanged(ctx.organizationId(), kind, response));
        return response;
    }

    @Transactional
    public TimelineItem comment(OrganizationContext ctx, UUID id, CommentRequest req) {
        ctx.require(MemberRole.OPERATOR);
        Incident incident = require(ctx, id);
        IncidentComment c = comments.save(new IncidentComment(incident.getId(), ctx.userId(), req.body().trim()));
        publisher.publishEvent(new IncidentChanged(ctx.organizationId(), "updated", toResponse(incident)));
        return new TimelineItem("comment", c.getCreatedAt(), userRef(ctx.userId()), "COMMENTED", null, null, c.getBody());
    }

    private void assign(OrganizationContext ctx, Incident incident, UUID assigneeId, Instant now) {
        members.findByUserIdAndOrganizationId(assigneeId, ctx.organizationId())
                .orElseThrow(() -> ApiException.badRequest("assignee_not_member", "O responsável precisa ser membro da organização."));
        String previous = incident.getAssigneeId() == null ? null : userName(incident.getAssigneeId());
        incident.setAssigneeId(assigneeId);
        history.save(new IncidentHistory(incident.getId(), ctx.userId(), EventType.ASSIGNED, previous, userName(assigneeId), null, now));
        notifications.notifyUser(ctx.organizationId(), assigneeId, Notification.Type.INCIDENT_ASSIGNED, incident.getSeverity(),
                "Você foi atribuído ao incidente #" + incident.getNumber(), incident.getTitle(),
                "/incidentes/" + incident.getId(), incident.getResourceId(), null,
                "incident_assigned:" + incident.getId() + ":" + assigneeId + ":" + now.toEpochMilli());
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(OrganizationContext ctx, Filters f, Pageable pageable) {
        Specification<Incident> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), ctx.organizationId()));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (Boolean.TRUE.equals(f.active())) p.add(root.get("status").in(Status.OPEN, Status.ACKNOWLEDGED, Status.INVESTIGATING));
            if (Boolean.FALSE.equals(f.active())) p.add(root.get("status").in(Status.RESOLVED, Status.CLOSED));
            if (f.severity() != null) p.add(cb.equal(root.get("severity"), f.severity()));
            if (f.origin() != null) p.add(cb.equal(root.get("origin"), f.origin()));
            if (f.resourceId() != null) p.add(cb.equal(root.get("resourceId"), f.resourceId()));
            if (f.assigneeId() != null) p.add(cb.equal(root.get("assigneeId"), f.assigneeId()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return incidents.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public IncidentDetail detail(OrganizationContext ctx, UUID id) {
        Incident incident = require(ctx, id);
        List<TimelineItem> timeline = new ArrayList<>();
        for (IncidentHistory h : history.findByIncidentIdOrderByCreatedAtAsc(id)) {
            timeline.add(new TimelineItem("history", h.getCreatedAt(), userRef(h.getUserId()), h.getEventType(),
                    h.getFromValue(), h.getToValue(), h.getDetails()));
        }
        for (IncidentComment c : comments.findByIncidentIdOrderByCreatedAtAsc(id)) {
            timeline.add(new TimelineItem("comment", c.getCreatedAt(), userRef(c.getUserId()), "COMMENTED", null, null, c.getBody()));
        }
        if (incident.getResourceId() != null) {
            Instant end = incident.getClosedAt() != null ? incident.getClosedAt()
                    : incident.getResolvedAt() != null ? incident.getResolvedAt() : Instant.now();
            for (ResourceStatusHistory t : statusHistory.findByResourceIdAndChangedAtGreaterThanEqualOrderByChangedAtAsc(
                    incident.getResourceId(), incident.getOpenedAt().minusSeconds(1))) {
                if (t.getChangedAt().isAfter(end.plusSeconds(1))) break;
                timeline.add(new TimelineItem("resource", t.getChangedAt(), null, "RESOURCE_STATUS",
                        t.getFromStatus().getLabel(), t.getToStatus().getLabel(), t.getReason()));
            }
        }
        timeline.sort((a, b) -> a.at().compareTo(b.at()));
        return new IncidentDetail(toResponse(incident), timeline, List.copyOf(TRANSITIONS.get(incident.getStatus())));
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> activeForResource(OrganizationContext ctx, UUID resourceId) {
        resources.findByIdAndOrganizationId(resourceId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
        return incidents.findActiveByResource(resourceId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IncidentSummary summary(OrganizationContext ctx) {
        List<Incident> active = incidents.findActive(ctx.organizationId());
        Instant dayAgo = Instant.now().minus(Duration.ofHours(24));
        List<Incident> resolvedRecently = incidents.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("organizationId"), ctx.organizationId()),
                cb.isNotNull(root.get("resolvedAt")),
                cb.greaterThanOrEqualTo(root.get("resolvedAt"), dayAgo)));
        Double mttr = null;
        String note = "MTTR = média de (resolução − abertura) dos incidentes resolvidos nas últimas 24 h.";
        if (!resolvedRecently.isEmpty()) {
            mttr = resolvedRecently.stream().mapToLong(i -> Duration.between(i.getOpenedAt(), i.getResolvedAt()).getSeconds()).average().orElse(0);
        } else {
            note += " Sem incidentes resolvidos no período: indicador indisponível.";
        }
        return new IncidentSummary(
                active.stream().filter(i -> i.getStatus() == Status.OPEN).count(),
                active.stream().filter(i -> i.getStatus() == Status.ACKNOWLEDGED).count(),
                active.stream().filter(i -> i.getStatus() == Status.INVESTIGATING).count(),
                resolvedRecently.size(),
                active.stream().filter(i -> i.getSeverity() == Severity.CRITICAL).count(),
                active.stream().filter(i -> i.getSeverity() == Severity.HIGH).count(),
                mttr, note);
    }

    public IncidentResponse toResponse(Incident i) {
        String resourceName = i.getResourceId() == null ? null
                : resources.findById(i.getResourceId()).map(MonitoredResource::getName).orElse("Recurso removido");
        return new IncidentResponse(i.getId(), i.getNumber(), i.getTitle(), i.getDescription(), i.getSeverity(),
                i.getSeverity().getLabel(), i.getStatus(), i.getStatus().getLabel(), i.getOrigin(), i.getOrigin().getLabel(),
                i.getResourceId(), resourceName, i.getAlertEventId(), userRef(i.getAssigneeId()), userRef(i.getCreatedBy()),
                i.getOpenedAt(), i.getAcknowledgedAt(), i.getResolvedAt(), i.getClosedAt(), i.durationSeconds(Instant.now()),
                i.getCause(), i.getNotes(), comments.countByIncidentId(i.getId()), i.getUpdatedAt());
    }

    private Incident require(OrganizationContext ctx, UUID id) {
        return incidents.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Incidente não encontrado."));
    }

    private UserRef userRef(UUID userId) {
        if (userId == null) return null;
        return new UserRef(userId, userName(userId));
    }

    private String userName(UUID userId) {
        return users.findById(userId).map(User::getFullName).orElse("Usuário removido");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
