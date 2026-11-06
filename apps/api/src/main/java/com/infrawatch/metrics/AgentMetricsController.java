package com.infrawatch.metrics;

import com.infrawatch.metrics.MetricsDtos.AgentMetricsResponse;
import com.infrawatch.metrics.MetricsDtos.AgentResponse;
import com.infrawatch.security.CurrentContextService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resources/{id}")
public class AgentMetricsController {

    private final AgentMetricsService metricsService;
    private final AgentService agentService;
    private final CurrentContextService context;

    public AgentMetricsController(AgentMetricsService metricsService, AgentService agentService,
                                  CurrentContextService context) {
        this.metricsService = metricsService;
        this.agentService = agentService;
        this.context = context;
    }

    @GetMapping("/agent-metrics")
    public AgentMetricsResponse agentMetrics(@PathVariable UUID id, @RequestParam(defaultValue = "6h") String period) {
        return metricsService.metrics(context.require(), id, period);
    }

    @GetMapping("/agent")
    public ResponseEntity<AgentResponse> agent(@PathVariable UUID id) {
        return agentService.forResource(context.require(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
