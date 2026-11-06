package com.infrawatch.alerts;

import com.infrawatch.metrics.Agent;
import com.infrawatch.metrics.AgentRepository;
import com.infrawatch.metrics.MetricMaintenanceJobs;
import com.infrawatch.metrics.MetricsProperties;
import com.infrawatch.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;

/** Utilitários de teste que precisam de beans não expostos pela API. */
final class AlertEngineHelpers {
    private AlertEngineHelpers() {}

    static void makeAgentStale(String resourceId, MockMvc mvc, AbstractIntegrationTest.Session s, AbstractIntegrationTest test) {
        AgentRepository agents = test.bean(AgentRepository.class);
        MetricsProperties props = test.bean(MetricsProperties.class);
        MetricMaintenanceJobs jobs = test.bean(MetricMaintenanceJobs.class);
        Agent agent = agents.findByResourceId(UUID.fromString(resourceId)).orElseThrow();
        agent.touch(Instant.now().minusSeconds(agent.getIntervalSeconds() * (long) props.staleAfterMultiplier() + 60), null, null, null);
        agents.save(agent);
        jobs.detectSilentAgents();
    }
}
