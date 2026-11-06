package com.infrawatch.resource;

import com.infrawatch.audit.AuditService;
import com.infrawatch.common.ApiException;
import com.infrawatch.monitoring.MonitoringProperties;
import com.infrawatch.monitoring.TargetRejectedException;
import com.infrawatch.monitoring.TargetValidator;
import com.infrawatch.resource.ResourceDtos.CheckConfigRequest;
import com.infrawatch.resource.ResourceDtos.CreateResourceRequest;
import com.infrawatch.resource.ResourceDtos.UpdateResourceRequest;
import com.infrawatch.security.OrganizationContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    public record Filters(String search, ResourceStatus status, ResourceType type, Environment environment,
                          UUID groupId, Boolean enabled) {}

    private final MonitoredResourceRepository resources;
    private final ResourceGroupRepository groups;
    private final TargetValidator targetValidator;
    private final MonitoringProperties monitoring;
    private final AuditService audit;

    public ResourceService(MonitoredResourceRepository resources, ResourceGroupRepository groups,
                           TargetValidator targetValidator, MonitoringProperties monitoring, AuditService audit) {
        this.resources = resources;
        this.groups = groups;
        this.targetValidator = targetValidator;
        this.monitoring = monitoring;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<MonitoredResource> list(OrganizationContext ctx, Filters f, Pageable pageable) {
        Specification<MonitoredResource> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), ctx.organizationId()));
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("targetUrl")), like)));
            }
            if (f.status() != null) predicates.add(cb.equal(root.get("currentStatus"), f.status()));
            if (f.type() != null) predicates.add(cb.equal(root.get("type"), f.type()));
            if (f.environment() != null) predicates.add(cb.equal(root.get("environment"), f.environment()));
            if (f.groupId() != null) predicates.add(cb.equal(root.get("group").get("id"), f.groupId()));
            if (f.enabled() != null) predicates.add(cb.equal(root.get("enabled"), f.enabled()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return resources.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public MonitoredResource get(OrganizationContext ctx, UUID id) {
        return resources.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
    }

    @Transactional
    public MonitoredResource create(OrganizationContext ctx, CreateResourceRequest req) {
        ctx.require(com.infrawatch.organization.MemberRole.ADMIN);
        String name = req.name().trim();
        if (resources.existsByOrganizationIdAndNameIgnoreCase(ctx.organizationId(), name)) {
            throw ApiException.conflict("Já existe um recurso com este nome na organização.");
        }
        String url = validateTarget(req.type(), req.targetUrl());
        MonitoredResource resource = new MonitoredResource(ctx.organizationId(), name, req.type(), url,
                req.environment(), ctx.userId());
        resource.setDescription(blankToNull(req.description()));
        resource.setOwner(blankToNull(req.owner()));
        resource.setTags(normalizeTags(req.tags()));
        if (req.groupId() != null) {
            resource.setGroup(requireGroup(ctx, req.groupId()));
        }
        applyCheckConfig(resource.getCheckConfig(), req.check());
        MonitoredResource saved = resources.save(resource);
        audit.record(ctx.organizationId(), ctx.userId(), "resource.created", "monitored_resource", saved.getId(),
                "Recurso \"" + saved.getName() + "\" (" + saved.getTargetUrl() + ") cadastrado");
        return saved;
    }

    @Transactional
    public MonitoredResource update(OrganizationContext ctx, UUID id, UpdateResourceRequest req) {
        ctx.require(com.infrawatch.organization.MemberRole.ADMIN);
        MonitoredResource resource = get(ctx, id);
        List<String> changes = new ArrayList<>();

        if (req.name() != null && !req.name().trim().equalsIgnoreCase(resource.getName())) {
            String name = req.name().trim();
            if (resources.existsByOrganizationIdAndNameIgnoreCase(ctx.organizationId(), name)) {
                throw ApiException.conflict("Já existe um recurso com este nome na organização.");
            }
            resource.setName(name);
            changes.add("nome");
        }
        if (req.type() != null && req.type() != resource.getType()) {
            resource.setType(req.type());
            changes.add("tipo");
        }
        if (req.targetUrl() != null && !req.targetUrl().trim().equals(resource.getTargetUrl())) {
            resource.setTargetUrl(validateTarget(resource.getType(), req.targetUrl()));
            // Novo destino: o histórico de contadores não se aplica mais.
            resource.resetCounters();
            resource.setNextCheckAt(Instant.now());
            changes.add("url");
        } else if (req.type() != null) {
            validateTarget(resource.getType(), resource.getTargetUrl());
        }
        if (req.description() != null) { resource.setDescription(blankToNull(req.description())); changes.add("descrição"); }
        if (req.environment() != null) { resource.setEnvironment(req.environment()); changes.add("ambiente"); }
        if (req.owner() != null) { resource.setOwner(blankToNull(req.owner())); changes.add("responsável"); }
        if (req.tags() != null) { resource.setTags(normalizeTags(req.tags())); changes.add("tags"); }
        if (Boolean.TRUE.equals(req.clearGroup())) { resource.setGroup(null); changes.add("grupo"); }
        else if (req.groupId() != null) { resource.setGroup(requireGroup(ctx, req.groupId())); changes.add("grupo"); }
        if (req.enabled() != null && req.enabled() != resource.isEnabled()) {
            resource.setEnabled(req.enabled());
            if (req.enabled()) {
                resource.setNextCheckAt(Instant.now());
            }
            changes.add(req.enabled() ? "monitoramento ativado" : "monitoramento pausado");
        }
        if (req.check() != null) {
            applyCheckConfig(resource.getCheckConfig(), req.check());
            changes.add("verificação");
        }
        audit.record(ctx.organizationId(), ctx.userId(), "resource.updated", "monitored_resource", resource.getId(),
                "Recurso \"" + resource.getName() + "\" atualizado: " + String.join(", ", changes));
        return resource;
    }

    @Transactional
    public void delete(OrganizationContext ctx, UUID id) {
        ctx.require(com.infrawatch.organization.MemberRole.ADMIN);
        MonitoredResource resource = get(ctx, id);
        resources.delete(resource);
        audit.record(ctx.organizationId(), ctx.userId(), "resource.deleted", "monitored_resource", id,
                "Recurso \"" + resource.getName() + "\" removido");
    }

    // ----- grupos -----

    @Transactional(readOnly = true)
    public List<ResourceGroup> listGroups(OrganizationContext ctx) {
        return groups.findByOrganizationIdOrderByNameAsc(ctx.organizationId());
    }

    @Transactional
    public ResourceGroup createGroup(OrganizationContext ctx, String name, String description) {
        ctx.require(com.infrawatch.organization.MemberRole.ADMIN);
        if (groups.existsByOrganizationIdAndNameIgnoreCase(ctx.organizationId(), name.trim())) {
            throw ApiException.conflict("Já existe um grupo com este nome.");
        }
        ResourceGroup group = groups.save(new ResourceGroup(ctx.organizationId(), name.trim(), blankToNull(description)));
        audit.record(ctx.organizationId(), ctx.userId(), "group.created", "resource_group", group.getId(),
                "Grupo \"" + group.getName() + "\" criado");
        return group;
    }

    @Transactional
    public ResourceGroup updateGroup(OrganizationContext ctx, UUID id, String name, String description) {
        ctx.require(com.infrawatch.organization.MemberRole.ADMIN);
        ResourceGroup group = requireGroup(ctx, id);
        if (!group.getName().equalsIgnoreCase(name.trim())
                && groups.existsByOrganizationIdAndNameIgnoreCase(ctx.organizationId(), name.trim())) {
            throw ApiException.conflict("Já existe um grupo com este nome.");
        }
        group.setName(name.trim());
        group.setDescription(blankToNull(description));
        return group;
    }

    @Transactional
    public void deleteGroup(OrganizationContext ctx, UUID id) {
        ctx.require(com.infrawatch.organization.MemberRole.ADMIN);
        ResourceGroup group = requireGroup(ctx, id);
        groups.delete(group);
        audit.record(ctx.organizationId(), ctx.userId(), "group.deleted", "resource_group", id,
                "Grupo \"" + group.getName() + "\" removido");
    }

    public long countInGroup(OrganizationContext ctx, UUID groupId) {
        return resources.count((root, q, cb) -> cb.and(cb.equal(root.get("organizationId"), ctx.organizationId()),
                cb.equal(root.get("group").get("id"), groupId)));
    }

    // ----- helpers -----

    private ResourceGroup requireGroup(OrganizationContext ctx, UUID groupId) {
        return groups.findByIdAndOrganizationId(groupId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Grupo não encontrado."));
    }

    private static final java.util.regex.Pattern HOST_ID = java.util.regex.Pattern.compile(
            "^[A-Za-z0-9]([A-Za-z0-9._-]{0,253})$");

    private String validateTarget(ResourceType type, String rawUrl) {
        if (type == ResourceType.AGENT_SERVER) {
            // Servidores com agente não são sondados pela rede: o campo identifica o host (nome/FQDN/IP).
            String host = rawUrl == null ? "" : rawUrl.trim();
            if (!HOST_ID.matcher(host).matches()) {
                throw ApiException.badRequest("target_host",
                        "Informe o identificador do servidor (nome, FQDN ou IP), sem esquema ou caminho.");
            }
            return host;
        }
        try {
            var uri = targetValidator.validateSyntax(rawUrl);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (type == ResourceType.HTTPS && !scheme.equals("https")) {
                throw ApiException.badRequest("target_scheme", "Recursos do tipo HTTPS exigem URL https://.");
            }
            if (type == ResourceType.HTTP && !scheme.equals("http")) {
                throw ApiException.badRequest("target_scheme", "Recursos do tipo HTTP exigem URL http://.");
            }
            return uri.toString();
        } catch (TargetRejectedException e) {
            throw ApiException.badRequest(e.getCode(), e.getMessage());
        }
    }

    private void applyCheckConfig(HealthCheckConfig config, CheckConfigRequest req) {
        if (req == null) return;
        if (req.intervalSeconds() != null) {
            if (req.intervalSeconds() < monitoring.minIntervalSeconds()) {
                throw ApiException.badRequest("interval_too_short",
                        "O intervalo mínimo permitido é " + monitoring.minIntervalSeconds() + " segundos.");
            }
            config.setIntervalSeconds(req.intervalSeconds());
        }
        if (req.timeoutMs() != null) {
            config.setTimeoutMs(Math.min(req.timeoutMs(), monitoring.maxTimeoutMs()));
        }
        if (req.httpMethod() != null) config.setHttpMethod(req.httpMethod().toUpperCase(Locale.ROOT));
        if (req.expectedStatusCodes() != null) config.setExpectedStatusCodes(req.expectedStatusCodes().replace(" ", ""));
        if (req.degradedLatencyMs() != null) config.setDegradedLatencyMs(req.degradedLatencyMs());
        if (req.failureThreshold() != null) config.setFailureThreshold(req.failureThreshold());
        if (req.recoveryThreshold() != null) config.setRecoveryThreshold(req.recoveryThreshold());
        if (req.followRedirects() != null) config.setFollowRedirects(req.followRedirects());
        if (config.getTimeoutMs() >= config.getIntervalSeconds() * 1000L) {
            throw ApiException.badRequest("timeout_vs_interval", "O timeout deve ser menor que o intervalo.");
        }
    }

    private static Set<String> normalizeTags(List<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        if (tags == null) return result;
        for (String t : tags) {
            if (t == null) continue;
            String tag = t.trim().toLowerCase(Locale.ROOT);
            if (!tag.isEmpty()) result.add(tag.substring(0, Math.min(40, tag.length())));
        }
        return result;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
