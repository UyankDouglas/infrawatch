package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Resolution;
import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.common.events.DomainEvents.AgentSampleReceived;
import com.infrawatch.common.events.DomainEvents.AlertChanged;
import com.infrawatch.common.events.DomainEvents.CheckCompleted;
import com.infrawatch.common.events.DomainEvents.ResourceStatusChanged;
import com.infrawatch.notifications.Notification;
import com.infrawatch.notifications.NotificationService;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de alertas. Consome eventos de domínio na mesma transação que os originou:
 *
 *  - Transições de estado: STATUS_DOWN / STATUS_DEGRADED / AGENT_NO_DATA abrem ao entrar no estado e resolvem ao sair.
 *  - Verificações HTTP: LATENCY (sucesso com tempo de resposta acima do limite).
 *  - Amostras do agente: CPU / MEMORY / DISK.
 *
 * Condições numéricas exigem {@code occurrences} violações consecutivas e {@code durationSeconds} desde a primeira
 * violação antes de abrir; uma leitura dentro do limite zera a contagem e resolve o alerta ativo (histerese simples).
 * Nunca existe mais de um alerta não resolvido por regra+recurso (índice único parcial) e nenhuma notificação é
 * repetida enquanto o mesmo alerta continua ativo.
 */
@Service
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final AlertRuleRepository rules;
    private final AlertEventRepository events;
    private final AlertConditionStateRepository states;
    private final MonitoredResourceRepository resources;
    private final NotificationService notifications;
    private final ApplicationEventPublisher publisher;
    private final AlertMapper mapper;
    private final Counter triggered;
    private final Counter resolved;

    public AlertEngine(AlertRuleRepository rules, AlertEventRepository events, AlertConditionStateRepository states,
                       MonitoredResourceRepository resources, NotificationService notifications,
                       ApplicationEventPublisher publisher, AlertMapper mapper, MeterRegistry registry) {
        this.rules = rules;
        this.events = events;
        this.states = states;
        this.resources = resources;
        this.notifications = notifications;
        this.publisher = publisher;
        this.mapper = mapper;
        this.triggered = Counter.builder("infrawatch.engine.alerts.triggered").register(registry);
        this.resolved = Counter.builder("infrawatch.engine.alerts.resolved").register(registry);
    }

    // ------------------------------------------------------------------ transições de estado

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStatusChanged(ResourceStatusChanged e) {
        if (e.to() == ResourceStatus.MAINTENANCE) return; // Fase 4: manutenção suprime alertas
        MonitoredResource resource = resources.findById(e.resourceId()).orElse(null);
        if (resource == null) return;
        UUID groupId = resource.getGroup() == null ? null : resource.getGroup().getId();
        List<AlertRule> active = rules.findByOrganizationIdAndEnabledTrue(e.organizationId());

        for (AlertRule rule : active) {
            if (!rule.appliesTo(resource.getId(), groupId)) continue;
            switch (rule.getMetric()) {
                case STATUS_DOWN -> toggleStateAlert(rule, resource, e.to() == ResourceStatus.DOWN, e,
                        "Recurso indisponível: " + resource.getName(),
                        reasonOr(e, "Indisponibilidade confirmada pelo motor de verificações."));
                case STATUS_DEGRADED -> toggleStateAlert(rule, resource, e.to() == ResourceStatus.DEGRADED, e,
                        "Recurso degradado: " + resource.getName(),
                        reasonOr(e, "Latência acima do limite de degradação."));
                case AGENT_NO_DATA -> {
                    if (resource.getType().isHttpCheck()) continue;
                    toggleStateAlert(rule, resource, e.to() == ResourceStatus.UNKNOWN, e,
                            "Agente sem dados: " + resource.getName(),
                            reasonOr(e, "O agente deixou de enviar métricas."));
                }
                default -> { }
            }
        }

        // Notificações de recurso (independentes de regra): indisponível / recuperado
        if (e.to() == ResourceStatus.DOWN) {
            notifications.notifyOrganization(e.organizationId(), Notification.Type.RESOURCE_DOWN, Severity.CRITICAL,
                    "Recurso indisponível: " + resource.getName(), reasonOr(e, ""),
                    "/recursos/" + resource.getId(), resource.getId(), null,
                    "resource_down:" + resource.getId() + ":" + e.at().toEpochMilli());
        } else if (e.from() == ResourceStatus.DOWN && e.to().isAvailable()) {
            notifications.notifyOrganization(e.organizationId(), Notification.Type.RESOURCE_RECOVERED, Severity.INFO,
                    "Recurso recuperado: " + resource.getName(), reasonOr(e, ""),
                    "/recursos/" + resource.getId(), resource.getId(), null,
                    "resource_recovered:" + resource.getId() + ":" + e.at().toEpochMilli());
        }
    }

    private void toggleStateAlert(AlertRule rule, MonitoredResource resource, boolean conditionActive,
                                  ResourceStatusChanged e, String title, String message) {
        Optional<AlertEvent> existing = events.findActive(rule.getId(), resource.getId());
        if (conditionActive) {
            if (existing.isPresent()) {
                existing.get().reevaluated(null, e.at());
                return;
            }
            open(rule, resource, title, message, null, e.at());
        } else if (existing.isPresent()) {
            close(existing.get(), resource, Resolution.CONDITION_CLEARED, null, e.at(),
                    reasonOr(e, "Condição normalizada."));
        }
    }

    // ------------------------------------------------------------------ verificações HTTP

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCheckCompleted(CheckCompleted e) {
        if (!e.success() || e.responseTimeMs() == null) return;
        evaluateNumeric(e.organizationId(), e.resourceId(), Metric.LATENCY, BigDecimal.valueOf(e.responseTimeMs()), e.at());
    }

    // ------------------------------------------------------------------ amostras do agente

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAgentSample(AgentSampleReceived e) {
        if (e.cpuPercent() != null) evaluateNumeric(e.organizationId(), e.resourceId(), Metric.CPU, e.cpuPercent(), e.at());
        if (e.memPercent() != null) evaluateNumeric(e.organizationId(), e.resourceId(), Metric.MEMORY, e.memPercent(), e.at());
        if (e.diskPercent() != null) evaluateNumeric(e.organizationId(), e.resourceId(), Metric.DISK, e.diskPercent(), e.at());
    }

    private void evaluateNumeric(UUID organizationId, UUID resourceId, Metric metric, BigDecimal value, Instant at) {
        MonitoredResource resource = resources.findById(resourceId).orElse(null);
        if (resource == null || resource.getCurrentStatus() == ResourceStatus.MAINTENANCE) return;
        UUID groupId = resource.getGroup() == null ? null : resource.getGroup().getId();

        for (AlertRule rule : rules.findByOrganizationIdAndEnabledTrue(organizationId)) {
            if (rule.getMetric() != metric || !rule.appliesTo(resource.getId(), groupId)) continue;
            if (rule.getOperator() == null || rule.getThreshold() == null) continue;

            boolean breach = rule.getOperator().test(value, rule.getThreshold());
            AlertConditionState state = states.findById(new AlertConditionState.Key(rule.getId(), resource.getId()))
                    .orElseGet(() -> new AlertConditionState(rule.getId(), resource.getId()));
            Optional<AlertEvent> existing = events.findActive(rule.getId(), resource.getId());

            if (breach) {
                state.breach(value, at);
                states.save(state);
                if (existing.isPresent()) {
                    existing.get().reevaluated(value, at);
                    continue;
                }
                boolean enoughOccurrences = state.getOccurrences() >= rule.getOccurrences();
                boolean enoughDuration = rule.getDurationSeconds() == 0 || (state.getBreachingSince() != null
                        && Duration.between(state.getBreachingSince(), at).getSeconds() >= rule.getDurationSeconds());
                if (enoughOccurrences && enoughDuration) {
                    String title = rule.getMetric().getLabel() + ": " + resource.getName();
                    String message = String.format("%s em %s %s %s (limite %s %s) por %d ocorrência(s)",
                            metricName(metric), formatValue(value), metric.getUnit(), rule.getOperator().getSymbol(),
                            formatValue(rule.getThreshold()), metric.getUnit(), state.getOccurrences());
                    open(rule, resource, title, message, value, at);
                }
            } else {
                state.clear(value, at);
                states.save(state);
                existing.ifPresent(ev -> close(ev, resource, Resolution.CONDITION_CLEARED, value, at,
                        metricName(metric) + " voltou a " + formatValue(value) + " " + metric.getUnit()));
            }
        }
    }

    // ------------------------------------------------------------------ abertura / fechamento

    private void open(AlertRule rule, MonitoredResource resource, String title, String message, BigDecimal value, Instant at) {
        AlertEvent event = events.save(new AlertEvent(rule.getOrganizationId(), rule.getId(), resource.getId(),
                rule.getMetric(), rule.getSeverity(), title, message, value, rule.getThreshold(), at));
        triggered.increment();
        int sent = notifications.notifyOrganization(rule.getOrganizationId(), Notification.Type.ALERT_TRIGGERED,
                rule.getSeverity(), "[" + rule.getSeverity().getLabel() + "] " + title, message,
                "/alertas?alerta=" + event.getId(), resource.getId(), event.getId(), "alert_triggered:" + event.getId());
        for (int i = 0; i < sent; i++) event.notified();
        publisher.publishEvent(new AlertChanged(rule.getOrganizationId(), "triggered", mapper.toResponse(event)));
        log.info("Alerta disparado: regra=\"{}\" recurso={} severidade={}", rule.getName(), resource.getId(), rule.getSeverity());
    }

    private void close(AlertEvent event, MonitoredResource resource, Resolution resolution, BigDecimal value,
                       Instant at, String note) {
        event.resolve(resolution, null, note, at);
        if (value != null) event.reevaluated(value, at);
        resolved.increment();
        notifications.notifyOrganization(event.getOrganizationId(), Notification.Type.ALERT_RESOLVED, Severity.INFO,
                "Resolvido: " + event.getTitle(), note, "/alertas?alerta=" + event.getId(), resource.getId(),
                event.getId(), "alert_resolved:" + event.getId());
        publisher.publishEvent(new AlertChanged(event.getOrganizationId(), "resolved", mapper.toResponse(event)));
        log.info("Alerta resolvido: {} ({})", event.getTitle(), resolution);
    }

    private static String reasonOr(ResourceStatusChanged e, String fallback) {
        return e.reason() == null || e.reason().isBlank() ? fallback : e.reason();
    }

    private static String metricName(Metric metric) {
        return switch (metric) {
            case LATENCY -> "Tempo de resposta";
            case CPU -> "Uso de CPU";
            case MEMORY -> "Uso de memória";
            case DISK -> "Uso de disco";
            default -> metric.getLabel();
        };
    }

    private static String formatValue(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
