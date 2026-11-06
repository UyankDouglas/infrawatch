package com.infrawatch.monitoring;

import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca como SEM DADOS os recursos cuja coleta foi interrompida (por exemplo, após o InfraWatch ficar parado):
 * quando a última verificação é mais antiga que {@code intervalo × staleAfterMultiplier}.
 * Isso diferencia "não sabemos" de "falha comprovada".
 */
@Component
@ConditionalOnProperty(prefix = "infrawatch.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StaleResourceDetector {

    private static final Logger log = LoggerFactory.getLogger(StaleResourceDetector.class);

    private final MonitoredResourceRepository resources;
    private final ResourceStatusHistoryRepository history;
    private final MonitoringProperties properties;
    private final MonitoringMetrics metrics;

    public StaleResourceDetector(MonitoredResourceRepository resources, ResourceStatusHistoryRepository history,
                                 MonitoringProperties properties, MonitoringMetrics metrics) {
        this.resources = resources;
        this.history = history;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "30s", initialDelayString = "20s")
    @Transactional
    public void detect() {
        Instant now = Instant.now();
        Instant coarseThreshold = now.minus(Duration.ofSeconds(
                (long) properties.minIntervalSeconds() * properties.staleAfterMultiplier()));
        List<MonitoredResource> candidates = resources.findStale(coarseThreshold);
        int marked = 0;
        for (MonitoredResource resource : candidates) {
            if (!resource.getType().isHttpCheck()) continue; // recursos com agente: ver MetricMaintenanceJobs
            long staleAfter = (long) resource.getCheckConfig().getIntervalSeconds() * properties.staleAfterMultiplier();
            Instant threshold = now.minusSeconds(staleAfter);
            if (resource.getLastCheckAt() != null && resource.getLastCheckAt().isBefore(threshold)) {
                ResourceStatus previous = resource.getCurrentStatus();
                resource.applyStatus(ResourceStatus.UNKNOWN, now);
                resource.resetCounters();
                history.save(new ResourceStatusHistory(resource.getId(), resource.getOrganizationId(), previous,
                        ResourceStatus.UNKNOWN, "Sem verificações há mais de " + staleAfter + " s", null, now));
                marked++;
            }
        }
        if (marked > 0) {
            metrics.stale(marked);
            log.warn("{} recurso(s) marcado(s) como SEM DADOS por ausência de coleta", marked);
        }
    }
}
