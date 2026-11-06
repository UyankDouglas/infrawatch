package com.infrawatch.monitoring;

import com.infrawatch.resource.MonitoredResourceRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Despachante periódico: reivindica recursos com verificação pendente (de forma atômica no banco) e os envia
 * para execução em paralelo, limitada pelo número de permissões disponíveis.
 */
@Component
@ConditionalOnProperty(prefix = "infrawatch.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CheckDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CheckDispatcher.class);

    private final MonitoredResourceRepository resources;
    private final CheckRunner runner;
    private final MonitoringMetrics metrics;
    private final MonitoringProperties properties;
    private final TransactionTemplate tx;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CheckDispatcher(MonitoredResourceRepository resources, CheckRunner runner, MonitoringMetrics metrics,
                           MonitoringProperties properties, PlatformTransactionManager transactionManager) {
        this.resources = resources;
        this.runner = runner;
        this.metrics = metrics;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${infrawatch.monitoring.dispatcher-interval:5s}", initialDelayString = "3s")
    public void dispatch() {
        int capacity = Math.min(properties.batchSize(), runner.availablePermits());
        if (capacity <= 0) {
            return;
        }
        List<UUID> due = tx.execute(status -> resources.claimDue(Instant.now(), capacity));
        if (due == null || due.isEmpty()) {
            return;
        }
        metrics.dispatched(due.size());
        log.debug("Despachando {} verificação(ões)", due.size());
        for (UUID id : due) {
            executor.submit(() -> runner.run(id, 1_000));
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
