package com.infrawatch.monitoring;

import com.infrawatch.monitoring.StatusEvaluator.Decision;
import com.infrawatch.monitoring.StatusEvaluator.State;
import com.infrawatch.monitoring.StatusEvaluator.Thresholds;
import com.infrawatch.resource.HealthCheckConfig;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste o resultado de uma verificação e aplica a transição de estado de forma atômica.
 * O recurso é bloqueado (SELECT ... FOR UPDATE) e resultados mais antigos que a última avaliação
 * são apenas registrados no histórico, sem sobrescrever um estado mais recente.
 */
@Service
public class CheckResultApplier {

    private static final Logger log = LoggerFactory.getLogger(CheckResultApplier.class);

    public record Applied(HealthCheckExecution execution, ResourceStatus previous, ResourceStatus current,
                          boolean transitioned, boolean evaluated) {}

    private final MonitoredResourceRepository resources;
    private final HealthCheckExecutionRepository executions;
    private final ResourceStatusHistoryRepository history;
    private final MonitoringMetrics metrics;

    public CheckResultApplier(MonitoredResourceRepository resources, HealthCheckExecutionRepository executions,
                              ResourceStatusHistoryRepository history, MonitoringMetrics metrics) {
        this.resources = resources;
        this.executions = executions;
        this.history = history;
        this.metrics = metrics;
    }

    @Transactional
    public Optional<Applied> apply(UUID resourceId, UUID runId, CheckOutcome outcome) {
        Optional<MonitoredResource> locked = resources.lockById(resourceId);
        if (locked.isEmpty()) {
            log.debug("Recurso {} removido antes de aplicar o resultado", resourceId);
            return Optional.empty();
        }
        MonitoredResource resource = locked.get();
        HealthCheckExecution execution = executions.save(new HealthCheckExecution(
                resource.getId(), resource.getOrganizationId(), runId, resource.getType().name(), outcome));
        metrics.recordCheck(outcome);

        if (resource.getLastEvaluatedAt() != null && !outcome.startedAt().isAfter(resource.getLastEvaluatedAt())) {
            log.info("Resultado atrasado ignorado para avaliação de estado (recurso={}, iniciado={}, últimaAvaliação={})",
                    resource.getId(), outcome.startedAt(), resource.getLastEvaluatedAt());
            return Optional.of(new Applied(execution, resource.getCurrentStatus(), resource.getCurrentStatus(), false, false));
        }

        HealthCheckConfig config = resource.getCheckConfig();
        ResourceStatus previous = resource.getCurrentStatus();
        Decision decision = StatusEvaluator.evaluate(
                new State(previous, resource.getConsecutiveFailures(), resource.getConsecutiveSuccesses(),
                        resource.getConsecutiveSlow()),
                new Thresholds(config.getFailureThreshold(), config.getRecoveryThreshold(), config.getDegradedLatencyMs()),
                outcome.success(), outcome.responseTimeMs(), outcome.errorCode());

        resource.recordCheck(outcome.startedAt(), outcome.success(), outcome.responseTimeMs(), outcome.httpStatus(),
                outcome.errorCode());
        resource.setCounters(decision.failures(), decision.successes(), decision.slow());

        boolean transitioned = decision.changed(previous);
        if (transitioned) {
            resource.applyStatus(decision.status(), outcome.finishedAt());
            history.save(new ResourceStatusHistory(resource.getId(), resource.getOrganizationId(), previous,
                    decision.status(), decision.reason(), execution.getId(), outcome.finishedAt()));
            metrics.transition();
            log.info("Transição de estado: recurso={} {} -> {} ({})", resource.getId(), previous, decision.status(),
                    decision.reason());
        }
        return Optional.of(new Applied(execution, previous, decision.status(), transitioned, true));
    }
}
