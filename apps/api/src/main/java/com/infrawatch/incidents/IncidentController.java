package com.infrawatch.incidents;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.common.PageResponse;
import com.infrawatch.incidents.Incident.Origin;
import com.infrawatch.incidents.Incident.Status;
import com.infrawatch.incidents.IncidentDtos.CommentRequest;
import com.infrawatch.incidents.IncidentDtos.CreateRequest;
import com.infrawatch.incidents.IncidentDtos.IncidentDetail;
import com.infrawatch.incidents.IncidentDtos.IncidentResponse;
import com.infrawatch.incidents.IncidentDtos.IncidentSummary;
import com.infrawatch.incidents.IncidentDtos.PatchRequest;
import com.infrawatch.incidents.IncidentDtos.StatusRequest;
import com.infrawatch.incidents.IncidentDtos.TimelineItem;
import com.infrawatch.incidents.IncidentService.Filters;
import com.infrawatch.security.CurrentContextService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class IncidentController {

    private final IncidentService service;
    private final CurrentContextService context;

    public IncidentController(IncidentService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping("/incidents")
    public PageResponse<IncidentResponse> list(@RequestParam(required = false) Status status,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(required = false) Severity severity,
                                               @RequestParam(required = false) Origin origin,
                                               @RequestParam(required = false) UUID resourceId,
                                               @RequestParam(required = false) UUID assigneeId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "openedAt"));
        return PageResponse.of(service.list(context.require(), new Filters(status, active, severity, origin, resourceId, assigneeId), pageable));
    }

    @GetMapping("/incidents/summary")
    public IncidentSummary summary() {
        return service.summary(context.require());
    }

    @GetMapping("/incidents/{id}")
    public IncidentDetail get(@PathVariable UUID id) {
        return service.detail(context.require(), id);
    }

    @PostMapping("/incidents")
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createManual(context.require(), body));
    }

    @PatchMapping("/incidents/{id}")
    public IncidentResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchRequest body) {
        return service.patch(context.require(), id, body);
    }

    @PostMapping("/incidents/{id}/status")
    public IncidentResponse status(@PathVariable UUID id, @Valid @RequestBody StatusRequest body) {
        return service.changeStatus(context.require(), id, body);
    }

    @PostMapping("/incidents/{id}/comments")
    public ResponseEntity<TimelineItem> comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.comment(context.require(), id, body));
    }

    @GetMapping("/resources/{id}/incidents")
    public List<IncidentResponse> forResource(@PathVariable UUID id) {
        return service.activeForResource(context.require(), id);
    }
}
