package com.infrawatch.maintenance;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Inicia e encerra janelas de manutenção nos horários programados. */
@Component
@ConditionalOnProperty(prefix = "infrawatch.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final MaintenanceService service;

    public MaintenanceScheduler(MaintenanceService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "20s", initialDelayString = "10s")
    public void tick() {
        Instant now = Instant.now();
        try {
            int started = service.activateDue(now);
            int finished = service.completeDue(now);
            if (started + finished > 0) {
                log.info("Manutenções: {} iniciada(s), {} encerrada(s)", started, finished);
            }
        } catch (RuntimeException e) {
            log.error("Falha ao processar janelas de manutenção", e);
        }
    }
}
