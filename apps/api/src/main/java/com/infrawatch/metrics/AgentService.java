package com.infrawatch.metrics;

import com.infrawatch.audit.AuditService;
import com.infrawatch.common.ApiException;
import com.infrawatch.metrics.MetricsDtos.AgentResponse;
import com.infrawatch.metrics.MetricsDtos.AgentTokenResponse;
import com.infrawatch.metrics.MetricsDtos.RegisterAgentRequest;
import com.infrawatch.metrics.AgentCredentialRepository;
import com.infrawatch.metrics.AgentRepository;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceType;
import com.infrawatch.security.OrganizationContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {

    private final AgentRepository agents;
    private final AgentCredentialRepository credentials;
    private final MonitoredResourceRepository resources;
    private final MetricsProperties properties;
    private final AuditService audit;

    public AgentService(AgentRepository agents, AgentCredentialRepository credentials,
                        MonitoredResourceRepository resources, MetricsProperties properties, AuditService audit) {
        this.agents = agents;
        this.credentials = credentials;
        this.resources = resources;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list(OrganizationContext ctx) {
        Map<UUID, String> names = resources.findByOrganizationIdOrderByNameAsc(ctx.organizationId()).stream()
                .collect(Collectors.toMap(MonitoredResource::getId, MonitoredResource::getName));
        return agents.findByOrganizationIdOrderByCreatedAtAsc(ctx.organizationId()).stream()
                .map(a -> toResponse(a, names.getOrDefault(a.getResourceId(), "Recurso removido")))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AgentResponse> forResource(OrganizationContext ctx, UUID resourceId) {
        MonitoredResource resource = requireResource(ctx, resourceId);
        return agents.findByResourceId(resourceId).map(a -> toResponse(a, resource.getName()));
    }

    /** Cria o agente para um recurso do tipo servidor e devolve o token uma única vez. */
    @Transactional
    public AgentTokenResponse register(OrganizationContext ctx, RegisterAgentRequest req) {
        ctx.require(MemberRole.ADMIN);
        MonitoredResource resource = requireResource(ctx, req.resourceId());
        if (resource.getType() != ResourceType.AGENT_SERVER) {
            throw ApiException.badRequest("agent_resource_type",
                    "Agentes só podem ser associados a recursos do tipo \"Servidor com agente autorizado\".");
        }
        if (agents.findByResourceId(resource.getId()).isPresent()) {
            throw ApiException.conflict("Este recurso já possui um agente. Use a rotação de token se necessário.");
        }
        Agent agent = agents.save(new Agent(ctx.organizationId(), resource.getId(), req.name().trim(),
                req.intervalSeconds() == null ? 30 : req.intervalSeconds(), ctx.userId()));
        AgentTokens.Generated generated = AgentTokens.generate();
        credentials.save(new AgentCredential(agent.getId(), generated.prefix(), generated.hash()));
        audit.record(ctx.organizationId(), ctx.userId(), "agent.registered", "agent", agent.getId(),
                "Agente \"" + agent.getName() + "\" registrado para o recurso \"" + resource.getName() + "\"");
        return new AgentTokenResponse(toResponse(agent, resource.getName()), generated.token(),
                "Guarde este token agora: ele não será exibido novamente.");
    }

    /** Gera um novo token e revoga os anteriores. */
    @Transactional
    public AgentTokenResponse rotate(OrganizationContext ctx, UUID agentId) {
        ctx.require(MemberRole.ADMIN);
        Agent agent = requireAgent(ctx, agentId);
        credentials.findByAgentIdAndRevokedAtIsNull(agent.getId()).forEach(AgentCredential::revoke);
        AgentTokens.Generated generated = AgentTokens.generate();
        credentials.save(new AgentCredential(agent.getId(), generated.prefix(), generated.hash()));
        agent.setStatus(Agent.Status.ACTIVE);
        audit.record(ctx.organizationId(), ctx.userId(), "agent.token_rotated", "agent", agent.getId(),
                "Token do agente \"" + agent.getName() + "\" rotacionado");
        String resourceName = resources.findById(agent.getResourceId()).map(MonitoredResource::getName).orElse("");
        return new AgentTokenResponse(toResponse(agent, resourceName), generated.token(),
                "Token anterior revogado. Atualize o agente com o novo token.");
    }

    @Transactional
    public AgentResponse revoke(OrganizationContext ctx, UUID agentId) {
        ctx.require(MemberRole.ADMIN);
        Agent agent = requireAgent(ctx, agentId);
        credentials.findByAgentIdAndRevokedAtIsNull(agent.getId()).forEach(AgentCredential::revoke);
        agent.setStatus(Agent.Status.REVOKED);
        audit.record(ctx.organizationId(), ctx.userId(), "agent.revoked", "agent", agent.getId(),
                "Agente \"" + agent.getName() + "\" revogado");
        String resourceName = resources.findById(agent.getResourceId()).map(MonitoredResource::getName).orElse("");
        return toResponse(agent, resourceName);
    }

    @Transactional
    public AgentResponse updateInterval(OrganizationContext ctx, UUID agentId, int intervalSeconds) {
        ctx.require(MemberRole.ADMIN);
        Agent agent = requireAgent(ctx, agentId);
        agent.setIntervalSeconds(intervalSeconds);
        String resourceName = resources.findById(agent.getResourceId()).map(MonitoredResource::getName).orElse("");
        return toResponse(agent, resourceName);
    }

    /** Seed/demonstração: registra um agente com token conhecido (fornecido por variável de ambiente). */
    @Transactional
    public Agent registerWithKnownToken(UUID organizationId, UUID resourceId, String name, int interval,
                                        String rawToken, UUID createdBy) {
        Agent agent = agents.save(new Agent(organizationId, resourceId, name, interval, createdBy));
        AgentTokens.Generated g = AgentTokens.fromRaw(rawToken);
        credentials.save(new AgentCredential(agent.getId(), g.prefix(), g.hash()));
        return agent;
    }

    public boolean isStale(Agent agent, Instant now) {
        if (agent.getLastSeenAt() == null) return false;
        long staleAfter = (long) agent.getIntervalSeconds() * properties.staleAfterMultiplier();
        return agent.getLastSeenAt().isBefore(now.minusSeconds(staleAfter));
    }

    public AgentResponse toResponse(Agent a, String resourceName) {
        String prefix = credentials.findByAgentIdAndRevokedAtIsNull(a.getId()).stream()
                .findFirst().map(AgentCredential::getTokenPrefix).orElse(null);
        return new AgentResponse(a.getId(), a.getResourceId(), resourceName, a.getName(), a.getStatus(),
                a.getIntervalSeconds(), a.getHostname(), a.getAgentVersion(), a.getLastSeenAt(), a.getLastSampleAt(),
                isStale(a, Instant.now()), prefix, a.getCreatedAt());
    }

    private MonitoredResource requireResource(OrganizationContext ctx, UUID resourceId) {
        return resources.findByIdAndOrganizationId(resourceId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
    }

    private Agent requireAgent(OrganizationContext ctx, UUID agentId) {
        return agents.findByIdAndOrganizationId(agentId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Agente não encontrado."));
    }
}
