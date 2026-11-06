package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertDtos.AlertResponse;
import com.infrawatch.alerts.AlertDtos.AlertSummary;
import com.infrawatch.alerts.AlertDtos.NoteRequest;
import com.infrawatch.alerts.AlertDtos.RulePatch;
import com.infrawatch.alerts.AlertDtos.RuleRequest;
import com.infrawatch.alerts.AlertDtos.RuleResponse;
import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.alerts.AlertEnums.Status;
import com.infrawatch.alerts.AlertService.Filters;
import com.infrawatch.common.PageResponse;
import com.infrawatch.security.CurrentContextService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AlertController {

    private final AlertService service;
    private final CurrentContextService context;

    public AlertController(AlertService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    // ----- alertas -----

    @GetMapping("/alerts")
    public PageResponse<AlertResponse> list(@RequestParam(required = false) Status status,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(required = false) Severity severity,
                                            @RequestParam(required = false) Metric metric,
                                            @RequestParam(required = false) UUID resourceId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "triggeredAt"));
        return PageResponse.of(service.list(context.require(), new Filters(status, active, severity, metric, resourceId), pageable));
    }

    @GetMapping("/alerts/summary")
    public AlertSummary summary() {
        return service.summary(context.require());
    }

    @GetMapping("/alerts/{id}")
    public AlertResponse get(@PathVariable UUID id) {
        return service.get(context.require(), id);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public AlertResponse acknowledge(@PathVariable UUID id, @Valid @RequestBody(required = false) NoteRequest body) {
        return service.acknowledge(context.require(), id, body == null ? null : body.note());
    }

    @PostMapping("/alerts/{id}/resolve")
    public AlertResponse resolve(@PathVariable UUID id, @Valid @RequestBody(required = false) NoteRequest body) {
        return service.resolveManually(context.require(), id, body == null ? null : body.note());
    }

    @GetMapping("/resources/{id}/alerts")
    public List<AlertResponse> forResource(@PathVariable UUID id) {
        return service.activeForResource(context.require(), id);
    }

    // ----- regras -----

    @GetMapping("/alert-rules")
    public List<RuleResponse> rules() {
        return service.listRules(context.require());
    }

    @GetMapping("/alert-rules/options")
    public Map<String, Object> options() {
        context.require();
        return Map.of(
                "metrics", List.of(Metric.values()).stream().map(m -> Map.of("value", m.name(), "label", m.getLabel(),
                        "numeric", m.isNumeric(), "unit", m.getUnit())).toList(),
                "operators", List.of(AlertEnums.Operator.values()).stream().map(o -> Map.of("value", o.name(), "label", o.getSymbol())).toList(),
                "severities", List.of(Severity.values()).stream().map(s -> Map.of("value", s.name(), "label", s.getLabel())).toList());
    }

    @PostMapping("/alert-rules")
    public ResponseEntity<RuleResponse> createRule(@Valid @RequestBody RuleRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRule(context.require(), body));
    }

    @PatchMapping("/alert-rules/{id}")
    public RuleResponse updateRule(@PathVariable UUID id, @Valid @RequestBody RulePatch body) {
        return service.updateRule(context.require(), id, body);
    }

    @DeleteMapping("/alert-rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        service.deleteRule(context.require(), id);
        return ResponseEntity.noContent().build();
    }
}
