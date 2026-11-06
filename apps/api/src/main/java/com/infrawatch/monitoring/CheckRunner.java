package com.infrawatch.monitoring;

import com.infrawatch.monitoring.CheckResultApplier.Applied;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Executa uma verificação completa (rede + persistência) para um recurso, respeitando o limite global de
 * concorrência. Usado tanto pelo despachante agendado quanto pela ação "verificar agora".
 */
@Service
public class CheckRunner {

    private static final Logger log = LoggerFactory.getLogger(CheckRunner.class);

    private final MonitoredResourceRepository resources;
    private final HttpCheckExecutor httpExecutor;
    private final CheckResultApplier applier;
    private final MonitoringMetrics metrics;
    private final Semaphore permits;

    public CheckRunner(MonitoredResourceRepository resources, HttpCheckExecutor httpExecutor,
                       CheckResultApplier applier, MonitoringMetrics metrics, MonitoringProperties properties) {
        this.resources = resources;
        this.httpExecutor = httpExecutor;
        this.applier = applier;
        this.metrics = metrics;
        this.permits = new Semaphore(properties.maxConcurrentChecks());
    }

    public int availablePermits() {
        return permits.availablePermits();
    }

    /** Executa a verificação; retorna vazio se o recurso não existe, não é do tipo HTTP ou se não houve permissão. */
    public Optional<Applied> run(UUID resourceId, long waitMillis) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (!acquired) {
            log.warn("Sem capacidade para verificar o recurso {} agora; será reagendado.", resourceId);
            return Optional.empty();
        }
        UUID runId = UUID.randomUUID();
        MDC.put("runId", runId.toString());
        metrics.started();
        try {
            Optional<MonitoredResource> found = resources.findById(resourceId);
            if (found.isEmpty() || !found.get().getType().isHttpCheck()) {
                return Optional.empty();
            }
            MonitoredResource resource = found.get();
            CheckOutcome outcome = httpExecutor.execute(resource.getTargetUrl(), resource.getCheckConfig());
            return applier.apply(resourceId, runId, outcome);
        } catch (RuntimeException e) {
            log.error("Falha inesperada ao verificar o recurso {}", resourceId, e);
            return Optional.empty();
        } finally {
            metrics.finished();
            permits.release();
            MDC.remove("runId");
        }
    }
}
