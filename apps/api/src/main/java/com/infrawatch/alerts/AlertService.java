package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertDtos.AlertResponse;
import com.infrawatch.alerts.AlertDtos.AlertSummary;
import com.infrawatch.alerts.AlertDtos.RulePatch;
import com.infrawatch.alerts.AlertDtos.RuleRequest;
import com.infrawatch.alerts.AlertDtos.RuleResponse;
import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Resolution;
import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.alerts.AlertEnums.Status;
import com.infrawatch.audit.AuditService;
import com.infrawatch.common.ApiException;
import com.infrawatch.common.events.DomainEvents.AlertChanged;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceGroupRepository;
import com.infrawatch.security.OrganizationContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    public record Filters(Status status, Boolean active, Severity severity, Metric metric, UUID resourceId) {}

    private final AlertRuleRepository rules;
    private final AlertEventRepository events;
    private final MonitoredResourceRepository resources;
    private final ResourceGroupRepository groups;
    private final AlertMapper mapper;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;

    public AlertService(AlertRuleRepository rules, AlertEventRepository events, MonitoredResourceRepository resources,
                        ResourceGroupRepository groups, AlertMapper mapper, AuditService audit,
                        ApplicationEventPublisher publisher) {
        this.rules = rules;
        this.events = events;
        this.resources = resources;
        this.groups = groups;
        this.mapper = mapper;
        this.audit = audit;
        this.publisher = publisher;
    }

    // ----- regras -----

    @Transactional(readOnly = true)
    public List<RuleResponse> listRules(OrganizationContext ctx) {
        return rules.findByOrganizationIdOrderByCreatedAtAsc(ctx.organizationId()).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public RuleResponse createRule(OrganizationContext ctx, RuleRequest req) {
        ctx.require(MemberRole.ADMIN);
        AlertRule rule = new AlertRule(ctx.organizationId(), req.name().trim(), req.metric(), req.severity(), ctx.userId());
        rule.setDescription(blank(req.description()));
        applyNumeric(rule, req.metric(), req.operator(), req.threshold());
        if (req.durationSeconds() != null) rule.setDurationSeconds(req.durationSeconds());
        if (req.occurrences() != null) rule.setOccurrences(req.occurrences());
        if (req.enabled() != null) rule.setEnabled(req.enabled());
        applyScope(ctx, rule, req.resourceId(), req.groupId());
        AlertRule saved = rules.save(rule);
        audit.record(ctx.organizationId(), ctx.userId(), "alert_rule.created", "alert_rule", saved.getId(),
                "Regra \"" + saved.getName() + "\" (" + saved.getMetric() + ") criada");
        return mapper.toResponse(saved);
    }

    @Transactional
    public RuleResponse updateRule(OrganizationContext ctx, UUID id, RulePatch req) {
        ctx.require(MemberRole.ADMIN);
        AlertRule rule = requireRule(ctx, id);
        if (req.name() != null) rule.setName(req.name().trim());
        if (req.description() != null) rule.setDescription(blank(req.description()));
        if (req.operator() != null || req.threshold() != null) {
            applyNumeric(rule, rule.getMetric(), req.operator() != null ? req.operator() : rule.getOperator(),
                    req.threshold() != null ? req.threshold() : rule.getThreshold());
        }
        if (req.durationSeconds() != null) rule.setDurationSeconds(req.durationSeconds());
        if (req.occurrences() != null) rule.setOccurrences(req.occurrences());
        if (req.severity() != null) rule.setSeverity(req.severity());
        if (Boolean.TRUE.equals(req.clearScope())) { rule.setResourceId(null); rule.setGroupId(null); }
        else if (req.resourceId() != null || req.groupId() != null) applyScope(ctx, rule, req.resourceId(), req.groupId());
        if (req.enabled() != null && req.enabled() != rule.isEnabled()) {
            rule.setEnabled(req.enabled());
            if (!req.enabled()) {
                // Desativar a regra resolve os alertas que ela mantém abertos.
                for (AlertEvent e : events.findActiveByRule(rule.getId())) {
                    e.resolve(Resolution.RULE_DISABLED, ctx.userId(), "Regra desativada", Instant.now());
                    publisher.publishEvent(new AlertChanged(ctx.organizationId(), "resolved", mapper.toResponse(e)));
                }
            }
        }
        audit.record(ctx.organizationId(), ctx.userId(), "alert_rule.updated", "alert_rule", rule.getId(),
                "Regra \"" + rule.getName() + "\" atualizada");
        return mapper.toResponse(rule);
    }

    @Transactional
    public void deleteRule(OrganizationContext ctx, UUID id) {
        ctx.require(MemberRole.ADMIN);
        AlertRule rule = requireRule(ctx, id);
        for (AlertEvent e : events.findActiveByRule(rule.getId())) {
            e.resolve(Resolution.RULE_DISABLED, ctx.userId(), "Regra removida", Instant.now());
        }
        rules.delete(rule);
        audit.record(ctx.organizationId(), ctx.userId(), "alert_rule.deleted", "alert_rule", id,
                "Regra \"" + rule.getName() + "\" removida");
    }

    @Transactional
    public int ensureDefaultRules(UUID organizationId, UUID createdBy) {
        if (rules.countByOrganizationId(organizationId) > 0) return 0;
        List<AlertRule> defaults = DefaultAlertRules.forOrganization(organizationId, createdBy);
        rules.saveAll(defaults);
        return defaults.size();
    }

    // ----- alertas -----

    @Transactional(readOnly = true)
    public Page<AlertResponse> list(OrganizationContext ctx, Filters f, Pageable pageable) {
        Specification<AlertEvent> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), ctx.organizationId()));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (Boolean.TRUE.equals(f.active())) p.add(cb.notEqual(root.get("status"), Status.RESOLVED));
            if (Boolean.FALSE.equals(f.active())) p.add(cb.equal(root.get("status"), Status.RESOLVED));
            if (f.severity() != null) p.add(cb.equal(root.get("severity"), f.severity()));
            if (f.metric() != null) p.add(cb.equal(root.get("metric"), f.metric()));
            if (f.resourceId() != null) p.add(cb.equal(root.get("resourceId"), f.resourceId()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return events.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AlertResponse get(OrganizationContext ctx, UUID id) {
        return mapper.toResponse(requireEvent(ctx, id));
    }

    @Transactional(readOnly = true)
    public AlertSummary summary(OrganizationContext ctx) {
        List<AlertEvent> active = events.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("organizationId"), ctx.organizationId()),
                cb.notEqual(root.get("status"), Status.RESOLVED)));
        return new AlertSummary(
                active.stream().filter(e -> e.getStatus() == Status.OPEN).count(),
                active.stream().filter(e -> e.getStatus() == Status.ACKNOWLEDGED).count(),
                active.stream().filter(e -> e.getSeverity() == Severity.CRITICAL).count(),
                active.stream().filter(e -> e.getSeverity() == Severity.HIGH).count(),
                active.stream().filter(e -> e.getSeverity() == Severity.WARNING).count(),
                active.stream().filter(e -> e.getSeverity() == Severity.INFO).count());
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> activeForResource(OrganizationContext ctx, UUID resourceId) {
        resources.findByIdAndOrganizationId(resourceId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
        return events.findActiveByResource(resourceId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public AlertResponse acknowledge(OrganizationContext ctx, UUID id, String note) {
        ctx.require(MemberRole.OPERATOR);
        AlertEvent e = requireEvent(ctx, id);
        if (e.getStatus() != Status.OPEN) {
            throw ApiException.conflict("Somente alertas abertos podem ser reconhecidos.");
        }
        e.acknowledge(ctx.userId(), blank(note), Instant.now());
        audit.record(ctx.organizationId(), ctx.userId(), "alert.acknowledged", "alert_event", e.getId(),
                "Alerta \"" + e.getTitle() + "\" reconhecido");
        AlertResponse response = mapper.toResponse(e);
        publisher.publishEvent(new AlertChanged(ctx.organizationId(), "acknowledged", response));
        return response;
    }

    @Transactional
    public AlertResponse resolveManually(OrganizationContext ctx, UUID id, String note) {
        ctx.require(MemberRole.OPERATOR);
        AlertEvent e = requireEvent(ctx, id);
        if (e.getStatus() == Status.RESOLVED) {
            throw ApiException.conflict("Este alerta já está resolvido.");
        }
        e.resolve(Resolution.MANUAL, ctx.userId(), blank(note), Instant.now());
        audit.record(ctx.organizationId(), ctx.userId(), "alert.resolved", "alert_event", e.getId(),
                "Alerta \"" + e.getTitle() + "\" resolvido manualmente");
        AlertResponse response = mapper.toResponse(e);
        publisher.publishEvent(new AlertChanged(ctx.organizationId(), "resolved", response));
        return response;
    }

    // ----- helpers -----

    private void applyNumeric(AlertRule rule, Metric metric, AlertEnums.Operator operator, java.math.BigDecimal threshold) {
        if (metric.isNumeric()) {
            if (operator == null || threshold == null) {
                throw ApiException.badRequest("rule_threshold_required",
                        "Regras de " + metric.getLabel().toLowerCase() + " exigem operador e limite.");
            }
            if (!metric.getUnit().equals("ms") && (threshold.signum() < 0 || threshold.compareTo(new java.math.BigDecimal("100")) > 0)) {
                throw ApiException.badRequest("rule_threshold_range", "Limites percentuais devem estar entre 0 e 100.");
            }
            rule.setOperator(operator);
            rule.setThreshold(threshold);
        } else {
            rule.setOperator(null);
            rule.setThreshold(null);
        }
    }

    private void applyScope(OrganizationContext ctx, AlertRule rule, UUID resourceId, UUID groupId) {
        if (resourceId != null) {
            resources.findByIdAndOrganizationId(resourceId, ctx.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
            rule.setResourceId(resourceId);
            rule.setGroupId(null);
        } else if (groupId != null) {
            groups.findByIdAndOrganizationId(groupId, ctx.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Grupo não encontrado."));
            rule.setGroupId(groupId);
            rule.setResourceId(null);
        }
    }

    private AlertRule requireRule(OrganizationContext ctx, UUID id) {
        return rules.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Regra não encontrada."));
    }

    private AlertEvent requireEvent(OrganizationContext ctx, UUID id) {
        return events.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Alerta não encontrado."));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
