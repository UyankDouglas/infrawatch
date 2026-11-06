package com.infrawatch.metrics;

import com.infrawatch.metrics.MetricsDtos.AgentResponse;
import com.infrawatch.metrics.MetricsDtos.AgentTokenResponse;
import com.infrawatch.metrics.MetricsDtos.RegisterAgentRequest;
import com.infrawatch.security.CurrentContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    public record IntervalRequest(@Min(10) @Max(3600) int intervalSeconds) {}

    private final AgentService service;
    private final CurrentContextService context;

    public AgentController(AgentService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping
    public List<AgentResponse> list() {
        return service.list(context.require());
    }

    @PostMapping("/register")
    public ResponseEntity<AgentTokenResponse> register(@Valid @RequestBody RegisterAgentRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(context.require(), body));
    }

    @PostMapping("/{id}/rotate")
    public AgentTokenResponse rotate(@PathVariable UUID id) {
        return service.rotate(context.require(), id);
    }

    @PatchMapping("/{id}")
    public AgentResponse update(@PathVariable UUID id, @Valid @RequestBody IntervalRequest body) {
        return service.updateInterval(context.require(), id, body.intervalSeconds());
    }

    @DeleteMapping("/{id}")
    public AgentResponse revoke(@PathVariable UUID id) {
        return service.revoke(context.require(), id);
    }
}
