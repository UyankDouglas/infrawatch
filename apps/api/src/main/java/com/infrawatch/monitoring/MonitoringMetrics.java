package com.infrawatch.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Métricas internas do InfraWatch (sobre o próprio motor), separadas das métricas dos recursos monitorados.
 * Prefixo: infrawatch.engine.*
 */
@Component
public class MonitoringMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Counter dispatched;
    private final Counter transitions;
    private final Counter staleMarked;

    public MonitoringMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.dispatched = Counter.builder("infrawatch.engine.checks.dispatched")
                .description("Verificações despachadas pelo agendador").register(registry);
        this.transitions = Counter.builder("infrawatch.engine.status.transitions")
                .description("Transições de estado de recursos").register(registry);
        this.staleMarked = Counter.builder("infrawatch.engine.resources.stale")
                .description("Recursos marcados como SEM DADOS por ausência de coleta").register(registry);
        Gauge.builder("infrawatch.engine.checks.inflight", inFlight, AtomicInteger::get)
                .description("Verificações em execução").register(registry);
    }

    public void dispatched(int count) { dispatched.increment(count); }
    public void started() { inFlight.incrementAndGet(); }
    public void finished() { inFlight.decrementAndGet(); }
    public void transition() { transitions.increment(); }
    public void stale(int count) { staleMarked.increment(count); }

    public void recordCheck(CheckOutcome outcome) {
        String result = outcome.success() ? "success" : "failure";
        String code = outcome.errorCode() == null ? "none" : outcome.errorCode();
        Counter.builder("infrawatch.engine.checks.executed").tag("result", result).tag("error", code)
                .register(registry).increment();
        Timer.builder("infrawatch.engine.checks.duration").tag("result", result).register(registry)
                .record(Duration.between(outcome.startedAt(), outcome.finishedAt()));
    }
}
