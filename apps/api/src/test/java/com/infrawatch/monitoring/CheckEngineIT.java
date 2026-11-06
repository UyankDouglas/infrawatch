package com.infrawatch.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercita o motor contra um servidor HTTP real embutido no teste (sem tocar destinos externos).
 */
class CheckEngineIT extends AbstractIntegrationTest {

    static HttpServer server;
    static final AtomicInteger STATUS = new AtomicInteger(200);
    static final AtomicLong DELAY_MS = new AtomicLong(0);
    static final AtomicInteger HITS = new AtomicInteger();

    @Autowired MonitoredResourceRepository resources;
    @Autowired HealthCheckExecutionRepository executions;
    @Autowired ResourceStatusHistoryRepository history;
    @Autowired CheckRunner runner;
    @Autowired CheckResultApplier applier;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            HITS.incrementAndGet();
            long delay = DELAY_MS.get();
            if (delay > 0) {
                try { Thread.sleep(delay); } catch (InterruptedException ignored) { }
            }
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(STATUS.get(), body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://10.0.0.1/internal");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() { server.stop(0); }

    @BeforeEach
    void reset() {
        STATUS.set(200);
        DELAY_MS.set(0);
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private String runNow(Session s, String id) throws Exception {
        return mvc.perform(post("/api/v1/resources/" + id + "/checks/run").with(csrf()).session(s.http()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("fluxo completo: saudável → falha controlada → indisponível → recuperação, com histórico persistido")
    void fullLifecycle() throws Exception {
        Session s = register("Admin", "Org Engine");
        String id = createResource(s, "Serviço embutido", baseUrl() + "/health",
                "{\"intervalSeconds\":30,\"timeoutMs\":2000,\"failureThreshold\":3,\"recoveryThreshold\":2,\"degradedLatencyMs\":800}");

        String r1 = runNow(s, id);
        assertThat((String) JsonPath.read(r1, "$.currentStatus")).isEqualTo("UP");
        assertThat((Boolean) JsonPath.read(r1, "$.transitioned")).isTrue();
        assertThat((Integer) JsonPath.read(r1, "$.execution.httpStatus")).isEqualTo(200);
        assertThat((Integer) JsonPath.read(r1, "$.execution.responseTimeMs")).isNotNull();

        STATUS.set(503);
        assertThat((String) JsonPath.read(runNow(s, id), "$.currentStatus")).isEqualTo("UP");
        assertThat((String) JsonPath.read(runNow(s, id), "$.currentStatus")).isEqualTo("UP");
        String down = runNow(s, id);
        assertThat((String) JsonPath.read(down, "$.currentStatus")).isEqualTo("DOWN");
        assertThat((String) JsonPath.read(down, "$.execution.errorCode")).isEqualTo("unexpected_status");

        STATUS.set(200);
        assertThat((String) JsonPath.read(runNow(s, id), "$.currentStatus")).isEqualTo("DOWN");
        assertThat((String) JsonPath.read(runNow(s, id), "$.currentStatus")).isEqualTo("UP");

        mvc.perform(get("/api/v1/resources/" + id + "/history").session(s.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].fromStatus").value("DOWN"))
                .andExpect(jsonPath("$.items[0].toStatus").value("UP"))
                .andExpect(jsonPath("$.items[1].fromStatus").value("UP"))
                .andExpect(jsonPath("$.items[1].toStatus").value("DOWN"))
                .andExpect(jsonPath("$.items[2].fromStatus").value("PENDING"));

        mvc.perform(get("/api/v1/resources/" + id + "/checks").session(s.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(6))
                .andExpect(jsonPath("$.items[*].errorMessage").exists());

        mvc.perform(get("/api/v1/resources/" + id + "/metrics").session(s.http()).param("period", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latency[0].total").value(6))
                .andExpect(jsonPath("$.observedSeconds").exists());

        mvc.perform(get("/api/v1/dashboard/overview").session(s.http()).param("period", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResources").value(1))
                .andExpect(jsonPath("$.available").value(1))
                .andExpect(jsonPath("$.checksInPeriod").value(6))
                .andExpect(jsonPath("$.failedChecksInPeriod").value(3))
                .andExpect(jsonPath("$.recentTransitions[0].toStatus").value("UP"));
    }

    @Test
    @DisplayName("latência acima do limite por três coletas leva a DEGRADADO")
    void degradation() throws Exception {
        Session s = register("Admin", "Org Degr");
        String id = createResource(s, "Lento", baseUrl() + "/health",
                "{\"intervalSeconds\":30,\"timeoutMs\":5000,\"failureThreshold\":3,\"recoveryThreshold\":2,\"degradedLatencyMs\":100}");
        runNow(s, id);
        DELAY_MS.set(250);
        runNow(s, id);
        runNow(s, id);
        String d = runNow(s, id);
        assertThat((String) JsonPath.read(d, "$.currentStatus")).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("timeout é registrado com código normalizado e não derruba o recurso na primeira falha")
    void timeoutIsNormalized() throws Exception {
        Session s = register("Admin", "Org Timeout");
        String id = createResource(s, "Timeout", baseUrl() + "/health", "{\"intervalSeconds\":30,\"timeoutMs\":300}");
        runNow(s, id);
        DELAY_MS.set(1500);
        String r = runNow(s, id);
        assertThat((String) JsonPath.read(r, "$.execution.errorCode")).isEqualTo("timeout");
        assertThat((String) JsonPath.read(r, "$.currentStatus")).isEqualTo("UP");
    }

    @Test
    @DisplayName("redirecionamento para endereço interno é recusado pela política de alvos")
    void redirectToInternalAddressIsRejected() throws Exception {
        Session s = register("Admin", "Org Redirect");
        String id = createResource(s, "Redirect", baseUrl() + "/redirect",
                "{\"intervalSeconds\":30,\"timeoutMs\":2000,\"followRedirects\":true}");
        String r = runNow(s, id);
        assertThat((Boolean) JsonPath.read(r, "$.execution.success")).isFalse();
        assertThat((String) JsonPath.read(r, "$.execution.errorCode")).isEqualTo("target_not_allowlisted");
    }

    @Test
    @DisplayName("conexão recusada vira falha com código connection_refused")
    void connectionRefused() throws Exception {
        Session s = register("Admin", "Org Refused");
        String id = createResource(s, "Porta fechada", "http://127.0.0.1:1/health", "{\"intervalSeconds\":30,\"timeoutMs\":1000}");
        String r = runNow(s, id);
        assertThat((Boolean) JsonPath.read(r, "$.execution.success")).isFalse();
        assertThat((String) JsonPath.read(r, "$.execution.errorCode")).isIn("connection_refused", "io_error", "connect_timeout");
    }

    @Test
    @DisplayName("resultado atrasado (fora de ordem) é persistido, mas não sobrescreve um estado mais recente")
    void staleResultDoesNotOverrideNewerState() throws Exception {
        Session s = register("Admin", "Org Order");
        String id = createResource(s, "Ordem", baseUrl() + "/health", "{\"intervalSeconds\":30,\"failureThreshold\":1}");
        UUID rid = UUID.fromString(id);
        runNow(s, id); // UP

        Instant old = Instant.now().minusSeconds(120);
        CheckOutcome stale = new CheckOutcome(false, 503, 5, "unexpected_status", "antigo", "127.0.0.1", old, old.plusMillis(5));
        var applied = applier.apply(rid, UUID.randomUUID(), stale).orElseThrow();

        assertThat(applied.evaluated()).isFalse();
        MonitoredResource r = resources.findById(rid).orElseThrow();
        assertThat(r.getCurrentStatus()).isEqualTo(ResourceStatus.UP);
        assertThat(executions.countByResourceId(rid)).isEqualTo(2);
    }

    @Test
    @DisplayName("o despachante reivindica apenas recursos vencidos e cada um uma única vez")
    void claimDueIsAtomic() throws Exception {
        Session s = register("Admin", "Org Claim");
        String id = createResource(s, "Vencido", baseUrl() + "/health", "{\"intervalSeconds\":60}");
        UUID rid = UUID.fromString(id);

        List<UUID> first = resources.claimDue(Instant.now(), 10);
        assertThat(first).contains(rid);
        List<UUID> second = resources.claimDue(Instant.now(), 10);
        assertThat(second).doesNotContain(rid);

        MonitoredResource r = resources.findById(rid).orElseThrow();
        assertThat(r.getNextCheckAt()).isAfter(Instant.now().plusSeconds(30));
    }

    @Test
    @DisplayName("recurso pausado não é reivindicado pelo despachante")
    void pausedResourceIsNotClaimed() throws Exception {
        Session s = register("Admin", "Org Paused");
        String id = createResource(s, "Pausado", baseUrl() + "/health", "{\"intervalSeconds\":60}");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/resources/" + id)
                        .with(csrf()).session(s.http())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        assertThat(resources.claimDue(Instant.now(), 10)).doesNotContain(UUID.fromString(id));
    }
}
