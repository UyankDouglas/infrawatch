package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertDtos.AlertResponse;
import com.infrawatch.alerts.AlertDtos.RuleResponse;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.auth.User;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceGroup;
import com.infrawatch.resource.ResourceGroupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertMapper {

    private final MonitoredResourceRepository resources;
    private final ResourceGroupRepository groups;
    private final AlertRuleRepository rules;
    private final AlertEventRepository events;
    private final UserRepository users;

    public AlertMapper(MonitoredResourceRepository resources, ResourceGroupRepository groups, AlertRuleRepository rules,
                       AlertEventRepository events, UserRepository users) {
        this.resources = resources;
        this.groups = groups;
        this.rules = rules;
        this.events = events;
        this.users = users;
    }

    public AlertResponse toResponse(AlertEvent e) {
        String ruleName = e.getRuleId() == null ? null : rules.findById(e.getRuleId()).map(AlertRule::getName).orElse("Regra removida");
        String resourceName = e.getResourceId() == null ? null
                : resources.findById(e.getResourceId()).map(MonitoredResource::getName).orElse("Recurso removido");
        Instant end = e.getResolvedAt() == null ? Instant.now() : e.getResolvedAt();
        return new AlertResponse(e.getId(), e.getRuleId(), ruleName, e.getResourceId(), resourceName, e.getMetric(),
                e.getMetric().getLabel(), e.getSeverity(), e.getSeverity().getLabel(), e.getStatus(),
                e.getStatus().getLabel(), e.getTitle(), e.getMessage(), e.getValue(), e.getThreshold(),
                e.getMetric().getUnit(), e.getTriggeredAt(), e.getLastEvaluatedAt(), e.getAcknowledgedAt(),
                e.getAcknowledgedBy(), userName(e.getAcknowledgedBy()), e.getAcknowledgeNote(), e.getResolvedAt(),
                e.getResolvedBy(), userName(e.getResolvedBy()), e.getResolution(), e.getResolutionNote(),
                e.getOccurrenceCount(), e.getNotificationCount(), Duration.between(e.getTriggeredAt(), end).getSeconds());
    }

    public RuleResponse toResponse(AlertRule r) {
        String resourceName = r.getResourceId() == null ? null
                : resources.findById(r.getResourceId()).map(MonitoredResource::getName).orElse("Recurso removido");
        String groupName = r.getGroupId() == null ? null
                : groups.findById(r.getGroupId()).map(ResourceGroup::getName).orElse("Grupo removido");
        long active = events.findActiveByRule(r.getId()).size();
        return new RuleResponse(r.getId(), r.getName(), r.getDescription(), r.getMetric(), r.getMetric().getLabel(),
                r.getOperator(), r.getThreshold(), r.getMetric().getUnit(), r.getDurationSeconds(), r.getOccurrences(),
                r.getSeverity(), r.getSeverity().getLabel(), r.getResourceId(), resourceName, r.getGroupId(), groupName,
                r.isEnabled(), active, r.getCreatedAt(), r.getUpdatedAt());
    }

    private String userName(UUID userId) {
        return userId == null ? null : users.findById(userId).map(User::getFullName).orElse("Usuário removido");
    }
}
