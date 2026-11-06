package com.infrawatch.incidents;

import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEvent;
import com.infrawatch.alerts.AlertEventRepository;
import com.infrawatch.common.events.DomainEvents.ResourceStatusChanged;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abertura e resolução automáticas de incidentes a partir das transições de estado.
 *  - → INDISPONÍVEL (fora de manutenção, recurso com auto_incident) abre um incidente automático, vinculado ao
 *    alerta de indisponibilidade quando existir.
 *  - INDISPONÍVEL → disponível resolve apenas incidentes automáticos; manuais nunca são encerrados sozinhos.
 * Executa depois do motor de alertas (ordem) para poder referenciar o alerta recém-criado.
 */
@Component
public class IncidentEngine {

    private static final Logger log = LoggerFactory.getLogger(IncidentEngine.class);

    private final IncidentService incidents;
    private final MonitoredResourceRepository resources;
    private final AlertEventRepository alerts;

    public IncidentEngine(IncidentService incidents, MonitoredResourceRepository resources, AlertEventRepository alerts) {
        this.incidents = incidents;
        this.resources = resources;
        this.alerts = alerts;
    }

    @EventListener
    @Order(20)
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStatusChanged(ResourceStatusChanged e) {
        MonitoredResource resource = resources.findById(e.resourceId()).orElse(null);
        if (resource == null) return;

        if (e.to() == ResourceStatus.DOWN) {
            if (!resource.isAutoIncident() || e.from() == ResourceStatus.MAINTENANCE) return;
            if (incidents.findActiveAutomaticExists(resource.getId())) return;
            String alertId = alerts.findActiveByResourceAndMetric(resource.getId(), Metric.STATUS_DOWN).stream()
                    .findFirst().map(AlertEvent::getId).map(Object::toString).orElse(null);
            incidents.openAutomatic(resource, alertId == null ? null : java.util.UUID.fromString(alertId),
                    e.reason() == null ? "Indisponibilidade confirmada" : e.reason(), e.at());
            log.info("Incidente automático aberto para o recurso {}", resource.getId());
            return;
        }

        if (e.from() == ResourceStatus.DOWN && e.to().isAvailable()) {
            incidents.autoResolve(resource, e.reason() == null ? "Recurso voltou a responder" : e.reason(), e.at());
        }

        // Toda transição durante um incidente ativo entra na timeline (eventos reais, nunca reescritos).
        incidents.recordResourceTransition(resource.getId(), e.from().getLabel(), e.to().getLabel(), e.reason(), e.at());
    }
}
