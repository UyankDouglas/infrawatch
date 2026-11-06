package com.infrawatch.metrics;

import com.infrawatch.metrics.AgentRepository;
import com.infrawatch.metrics.MetricRollupRepository;
import com.infrawatch.metrics.MetricSampleRepository;
import com.infrawatch.monitoring.HealthCheckExecutionRepository;
import com.infrawatch.monitoring.ResourceStatusHistory;
import com.infrawatch.monitoring.ResourceStatusHistoryRepository;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.infrawatch.common.events.DomainEvents.ResourceStatusChanged;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarefas de manutenção das métricas: agregação (5 min / 1 h), retenção e detecção de agente silencioso.
 * Políticas em {@link MetricsProperties}; nada aqui apaga histórico de transições ou auditoria.
 */
@Component
public class MetricMaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MetricMaintenanceJobs.class);

    private final MetricSampleRepository samples;
    private final MetricRollupRepository rollups;
    private final HealthCheckExecutionRepository executions;
    private final AgentRepository agents;
    private final MonitoredResourceRepository resources;
    private final ResourceStatusHistoryRepository history;
    private final AgentService agentService;
    private final MetricsProperties properties;
    private final ApplicationEventPublisher events;

    public MetricMaintenanceJobs(MetricSampleRepository samples, MetricRollupRepository rollups,
                                 HealthCheckExecutionRepository executions, AgentRepository agents,
                                 MonitoredResourceRepository resources, ResourceStatusHistoryRepository history,
                                 AgentService agentService, MetricsProperties properties,
                                 ApplicationEventPublisher events) {
        this.events = events;
        this.samples = samples;
        this.rollups = rollups;
        this.executions = executions;
        this.agents = agents;
        this.resources = resources;
        this.history = history;
        this.agentService = agentService;
        this.properties = properties;
    }

    /** Agrega as amostras das últimas 3 h (idempotente: buckets já existentes são atualizados). */
    @Scheduled(fixedDelayString = "5m", initialDelayString = "1m")
    @Transactional
    public void rollup() {
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofHours(3));
        int r5 = samples.rollup(from, now, 300);
        int r1h = samples.rollup(now.minus(Duration.ofHours(6)), now, 3600);
        log.debug("Rollups atualizados: {} buckets de 5 min, {} buckets de 1 h", r5, r1h);
    }

    /** Aplica a política de retenção uma vez por hora. */
    @Scheduled(fixedDelayString = "1h", initialDelayString = "2m")
    @Transactional
    public void retention() {
        Instant now = Instant.now();
        int rawDeleted = samples.deleteOlderThan(now.minus(properties.rawRetention()));
        int rollupDeleted = rollups.deleteOlderThan(now.minus(properties.rollupRetention()));
        int checksDeleted = executions.deleteOlderThan(now.minus(properties.checkRetention()));
        if (rawDeleted + rollupDeleted + checksDeleted > 0) {
            log.info("Retenção aplicada: {} amostras brutas, {} rollups, {} execuções de verificação removidas",
                    rawDeleted, rollupDeleted, checksDeleted);
        }
    }

    /** Agente silencioso: recurso vai para SEM DADOS (não para INDISPONÍVEL — ausência de coleta ≠ falha). */
    @Scheduled(fixedDelayString = "30s", initialDelayString = "30s")
    @Transactional
    public void detectSilentAgents() {
        Instant now = Instant.now();
        for (Agent agent : agents.findByStatus(Agent.Status.ACTIVE)) {
            if (!agentService.isStale(agent, now)) continue;
            resources.findById(agent.getResourceId()).ifPresent(resource -> {
                if (resource.getCurrentStatus().isKnown()) {
                    markUnknown(resource, agent, now);
                }
            });
        }
    }

    private void markUnknown(MonitoredResource resource, Agent agent, Instant now) {
        ResourceStatus previous = resource.getCurrentStatus();
        resource.applyStatus(ResourceStatus.UNKNOWN, now);
        resource.resetCounters();
        history.save(new ResourceStatusHistory(resource.getId(), resource.getOrganizationId(), previous,
                ResourceStatus.UNKNOWN, "Agente \"" + agent.getName() + "\" sem amostras há mais de "
                + agent.getIntervalSeconds() * properties.staleAfterMultiplier() + " s", null, now));
        log.warn("Recurso {} marcado como SEM DADOS: agente {} silencioso", resource.getId(), agent.getId());
        events.publishEvent(new ResourceStatusChanged(resource.getOrganizationId(), resource.getId(), resource.getName(),
                previous, ResourceStatus.UNKNOWN, "Agente \"" + agent.getName() + "\" sem amostras", now, "agent"));
    }
}
