package com.infrawatch.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.metrics.AgentRepository;
import com.infrawatch.metrics.MetricRollupRepository;
import com.infrawatch.metrics.MetricSampleRepository;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AgentIngestIT extends AbstractIntegrationTest {

    @Autowired AgentRepository agents;
    @Autowired MetricSampleRepository samples;
    @Autowired MetricRollupRepository rollups;
    @Autowired MetricMaintenanceJobs jobs;
    @Autowired MetricsProperties properties;

    private String createServer(Session s, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/resources").with(csrf()).session(s.http())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"type\":\"AGENT_SERVER\",\"targetUrl\":\"srv-01.interno\",\"environment\":\"PRODUCTION\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String registerAgent(Session s, String resourceId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/agents/register").with(csrf()).session(s.http())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"%s\",\"name\":\"agente-01\",\"intervalSeconds\":10}".formatted(resourceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.agent.status").value("ACTIVE"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private static String samplePayload(double cpu) {
        return """
                {"sampledAt":"%s","cpuPercent":%s,"loadAvg1m":0.42,
                 "memory":{"totalBytes":8589934592,"usedBytes":4294967296},
                 "disk":{"path":"/","totalBytes":107374182400,"usedBytes":85899345920},
                 "uptimeSeconds":86400,"hostname":"srv-01","agentVersion":"0.2.0"}
                """.formatted(Instant.now(), cpu);
    }

    @Test
    @DisplayName("agente registrado envia amostra: métricas gravadas, percentuais calculados e recurso vai para DISPONÍVEL")
    void ingestHappyPath() throws Exception {
        Session s = register("Admin", "Org Agente");
        String resourceId = createServer(s, "Servidor 01");
        String token = registerAgent(s, resourceId);

        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(samplePayload(37.5)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextIntervalSeconds").value(10))
                .andExpect(jsonPath("$.ignoredMetrics").isEmpty());

        mvc.perform(get("/api/v1/resources/" + resourceId).session(s.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/api/v1/resources/" + resourceId + "/agent-metrics").session(s.http()).param("period", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAgent").value(true))
                .andExpect(jsonPath("$.source").value("agent"))
                .andExpect(jsonPath("$.latest.cpuPercent").value(37.5))
                .andExpect(jsonPath("$.latest.memPercent").value(50.0))
                .andExpect(jsonPath("$.latest.diskPercent").value(80.0))
                .andExpect(jsonPath("$.latest.uptimeSeconds").value(86400))
                .andExpect(jsonPath("$.missingMetrics").isEmpty())
                .andExpect(jsonPath("$.series[0].samples").value(1))
                .andExpect(jsonPath("$.agent.hostname").value("srv-01"));

        mvc.perform(get("/api/v1/resources/" + resourceId + "/history").session(s.http()))
                .andExpect(jsonPath("$.items[0].toStatus").value("UP"))
                .andExpect(jsonPath("$.items[0].reason").value(org.hamcrest.Matchers.containsString("agente")));
    }

    @Test
    @DisplayName("métricas não suportadas são registradas como indisponíveis, nunca inventadas")
    void missingMetricsAreReportedAsUnavailable() throws Exception {
        Session s = register("Admin", "Org Parcial");
        String resourceId = createServer(s, "Servidor parcial");
        String token = registerAgent(s, resourceId);

        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpuPercent\":12.3,\"memory\":null,\"disk\":null,\"uptimeSeconds\":null}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ignoredMetrics", org.hamcrest.Matchers.containsInAnyOrder("memory", "disk", "uptime")));

        mvc.perform(get("/api/v1/resources/" + resourceId + "/agent-metrics").session(s.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingMetrics", org.hamcrest.Matchers.containsInAnyOrder("memory", "disk", "uptime", "load")))
                .andExpect(jsonPath("$.values[?(@.key=='memory')].available").value(false))
                .andExpect(jsonPath("$.values[?(@.key=='cpu')].available").value(true));
    }

    @Test
    @DisplayName("token inválido, revogado ou ausente é recusado")
    void rejectsBadTokens() throws Exception {
        Session s = register("Admin", "Org Token");
        String resourceId = createServer(s, "Servidor token");
        String token = registerAgent(s, resourceId);

        mvc.perform(post("/api/v1/agents/ingest").contentType(MediaType.APPLICATION_JSON).content(samplePayload(1)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("agent_unauthorized"));
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer iwa_nope1234.xyz")
                        .contentType(MediaType.APPLICATION_JSON).content(samplePayload(1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "tampered")
                        .contentType(MediaType.APPLICATION_JSON).content(samplePayload(1)))
                .andExpect(status().isUnauthorized());

        String agentId = agents.findByResourceId(UUID.fromString(resourceId)).orElseThrow().getId().toString();
        mvc.perform(delete("/api/v1/agents/" + agentId).with(csrf()).session(s.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(samplePayload(1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rotação invalida o token anterior e o novo passa a funcionar")
    void rotateToken() throws Exception {
        Session s = register("Admin", "Org Rotate");
        String resourceId = createServer(s, "Servidor rotate");
        String oldToken = registerAgent(s, resourceId);
        String agentId = agents.findByResourceId(UUID.fromString(resourceId)).orElseThrow().getId().toString();

        String newToken = JsonPath.read(mvc.perform(post("/api/v1/agents/" + agentId + "/rotate").with(csrf()).session(s.http()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
        assertThat(newToken).isNotEqualTo(oldToken);

        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON).content(samplePayload(5)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON).content(samplePayload(5)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("frequência acima do permitido retorna 429; relógio defasado retorna 400")
    void rateLimitAndClockSkew() throws Exception {
        Session s = register("Admin", "Org Rate");
        String resourceId = createServer(s, "Servidor rate");
        String token = registerAgent(s, resourceId);

        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(samplePayload(1))).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(samplePayload(2))).andExpect(status().isTooManyRequests());

        Session s2 = register("Admin", "Org Skew");
        String r2 = createServer(s2, "Servidor skew");
        String t2 = registerAgent(s2, r2);
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + t2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampledAt\":\"2020-01-01T00:00:00Z\",\"cpuPercent\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("sample_clock_skew"));
    }

    @Test
    @DisplayName("agente só pode ser associado a recurso do tipo servidor e é isolado por organização")
    void registrationRules() throws Exception {
        Session s = register("Admin", "Org Regras");
        String http = createResource(s, "HTTP", "http://localhost:9101/health");
        mvc.perform(post("/api/v1/agents/register").with(csrf()).session(s.http()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"%s\",\"name\":\"x\"}".formatted(http)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("agent_resource_type"));

        String server = createServer(s, "Servidor regras");
        registerAgent(s, server);
        mvc.perform(post("/api/v1/agents/register").with(csrf()).session(s.http()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"%s\",\"name\":\"x\"}".formatted(server)))
                .andExpect(status().isConflict());

        Session other = register("Outra", "Org Outra");
        mvc.perform(get("/api/v1/agents").session(other.http())).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/resources/" + server + "/agent-metrics").session(other.http())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/agents/register").with(csrf()).session(other.http()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"%s\",\"name\":\"x\"}".formatted(server)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rollup agrega amostras em buckets e a retenção remove dados antigos")
    void rollupAndRetention() throws Exception {
        Session s = register("Admin", "Org Rollup");
        String resourceId = createServer(s, "Servidor rollup");
        String token = registerAgent(s, resourceId);
        UUID rid = UUID.fromString(resourceId);

        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(samplePayload(20))).andExpect(status().isAccepted());

        // Amostra antiga inserida diretamente (fora da janela de retenção)
        Agent agent = agents.findByResourceId(rid).orElseThrow();
        MetricSample old = new MetricSample(rid, s.organizationId(), agent.getId(),
                Instant.now().minus(properties.rawRetention()).minusSeconds(3600), Instant.now());
        old.setCpu(new java.math.BigDecimal("90.00"), null);
        samples.save(old);
        assertThat(samples.countByResourceId(rid)).isEqualTo(2);

        jobs.rollup();
        assertThat(rollups.countByResourceIdAndBucketSeconds(rid, 300)).isEqualTo(1);
        assertThat(rollups.countByResourceIdAndBucketSeconds(rid, 3600)).isEqualTo(1);

        jobs.retention();
        assertThat(samples.countByResourceId(rid)).isEqualTo(1);
    }

    @Test
    @DisplayName("agente silencioso leva o recurso a SEM DADOS, não a INDISPONÍVEL")
    void silentAgentBecomesUnknown() throws Exception {
        Session s = register("Admin", "Org Silencio");
        String resourceId = createServer(s, "Servidor silencioso");
        String token = registerAgent(s, resourceId);
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(samplePayload(20))).andExpect(status().isAccepted());

        // Simula ausência de contato: lastSeenAt no passado além de intervalo × multiplicador
        Agent agent = agents.findByResourceId(UUID.fromString(resourceId)).orElseThrow();
        agent.touch(Instant.now().minusSeconds(10L * properties.staleAfterMultiplier() + 60), null, null, null);
        agents.save(agent);

        jobs.detectSilentAgents();

        mvc.perform(get("/api/v1/resources/" + resourceId).session(s.http()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.statusLabel").value("Sem dados"));
        mvc.perform(get("/api/v1/resources/" + resourceId + "/agent-metrics").session(s.http()))
                .andExpect(jsonPath("$.stale").value(true));
    }
}
