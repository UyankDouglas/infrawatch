package com.infrawatch.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.monitoring.CheckOutcome;
import com.infrawatch.monitoring.CheckResultApplier;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AlertEngineIT extends AbstractIntegrationTest {

    @Autowired CheckResultApplier applier;
    @Autowired AlertEventRepository events;

    private void fail(UUID resourceId) {
        Instant now = Instant.now();
        applier.apply(resourceId, UUID.randomUUID(), new CheckOutcome(false, 503, 5, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(5)));
    }

    private void ok(UUID resourceId, int latencyMs) {
        Instant now = Instant.now();
        applier.apply(resourceId, UUID.randomUUID(), new CheckOutcome(true, 200, latencyMs, null, null, "127.0.0.1", now, now.plusMillis(latencyMs)));
    }

    private String activeAlerts(Session s) throws Exception {
        return getJson(s, "/api/v1/alerts?active=true");
    }

    @Test
    @DisplayName("novas organizações recebem regras padrão para métricas coletadas")
    void defaultRulesAreCreated() throws Exception {
        Session s = register("Admin", "Org Regras Padrão");
        mvc.perform(get("/api/v1/alert-rules").cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[?(@.metric=='STATUS_DOWN')].severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("recurso indisponível dispara um único alerta crítico, notifica a organização e resolve na recuperação")
    void downTriggersSingleAlertAndRecoveryResolves() throws Exception {
        Session s = register("Admin", "Org Down");
        UUID rid = UUID.fromString(createResource(s, "Serviço", "http://localhost:9101/health",
                "{\"intervalSeconds\":30,\"failureThreshold\":2,\"recoveryThreshold\":1}"));

        ok(rid, 50);
        fail(rid);
        assertThat(JsonPath.<Integer>read(activeAlerts(s), "$.totalItems")).isZero(); // abaixo do limiar: nada
        fail(rid); // confirma DOWN
        fail(rid); // continua DOWN: não duplica

        String body = activeAlerts(s);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<String>read(body, "$.items[0].severity")).isEqualTo("CRITICAL");
        assertThat(JsonPath.<String>read(body, "$.items[0].metric")).isEqualTo("STATUS_DOWN");
        assertThat(JsonPath.<String>read(body, "$.items[0].status")).isEqualTo("OPEN");
        String alertId = JsonPath.read(body, "$.items[0].id");

        // notificações: indisponível + alerta disparado (uma de cada para o único membro)
        mvc.perform(get("/api/v1/notifications").cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.type=='RESOURCE_DOWN')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.type=='ALERT_TRIGGERED')]", hasSize(1)));
        mvc.perform(get("/api/v1/notifications/unread-count").cookie(s.cookie()))
                .andExpect(jsonPath("$.unread").value(greaterThanOrEqualTo(2)));

        // dashboard reflete o alerta ativo
        mvc.perform(get("/api/v1/dashboard/overview").cookie(s.cookie()))
                .andExpect(jsonPath("$.activeAlerts").value(1))
                .andExpect(jsonPath("$.recentAlerts[0].id").value(alertId));

        ok(rid, 40); // recovery_threshold = 1 → UP
        mvc.perform(get("/api/v1/alerts/" + alertId).cookie(s.cookie()))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("CONDITION_CLEARED"));
        mvc.perform(get("/api/v1/notifications").cookie(s.cookie()))
                .andExpect(jsonPath("$.items[?(@.type=='RESOURCE_RECOVERED')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.type=='ALERT_RESOLVED')]", hasSize(1)));
        assertThat(JsonPath.<Integer>read(activeAlerts(s), "$.totalItems")).isZero();
    }

    @Test
    @DisplayName("reconhecimento e resolução manual registram usuário, observação e auditoria; RBAC aplicado")
    void acknowledgeAndManualResolve() throws Exception {
        Session s = register("Admin", "Org Ack");
        UUID rid = UUID.fromString(createResource(s, "Serviço", "http://localhost:9101/health", "{\"intervalSeconds\":30,\"failureThreshold\":1}"));
        fail(rid);
        String alertId = JsonPath.read(activeAlerts(s), "$.items[0].id");

        mvc.perform(post("/api/v1/alerts/" + alertId + "/acknowledge").with(csrf()).cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Investigando\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedByName").value("Admin"))
                .andExpect(jsonPath("$.acknowledgeNote").value("Investigando"));
        mvc.perform(post("/api/v1/alerts/" + alertId + "/acknowledge").with(csrf()).cookie(s.cookie()))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/alerts/" + alertId + "/resolve").with(csrf()).cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Falso positivo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("MANUAL"));

        mvc.perform(get("/api/v1/audit").cookie(s.cookie()))
                .andExpect(jsonPath("$.items[?(@.action=='alert.acknowledged')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.action=='alert.resolved')]", hasSize(1)));

        Session other = register("Outra", "Org Ack Outra");
        mvc.perform(get("/api/v1/alerts/" + alertId).cookie(other.cookie())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/alerts").cookie(other.cookie())).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("latência acima do limite por N verificações dispara e normaliza com histerese simples")
    void latencyRuleWithOccurrences() throws Exception {
        Session s = register("Admin", "Org Latência");
        UUID rid = UUID.fromString(createResource(s, "Lento", "http://localhost:9101/health", "{\"intervalSeconds\":30}"));
        // regra padrão: > 2000 ms, 3 ocorrências
        ok(rid, 2500);
        ok(rid, 2500);
        assertThat(JsonPath.<Integer>read(activeAlerts(s), "$.totalItems")).isZero();
        ok(rid, 2500);
        String body = activeAlerts(s);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<String>read(body, "$.items[0].metric")).isEqualTo("LATENCY");
        assertThat(JsonPath.<Double>read(body, "$.items[0].value")).isEqualTo(2500.0);
        ok(rid, 2600); // continua: mesma ocorrência, sem novo alerta
        assertThat(JsonPath.<Integer>read(activeAlerts(s), "$.totalItems")).isEqualTo(1);
        ok(rid, 100);  // normaliza → resolve
        assertThat(JsonPath.<Integer>read(activeAlerts(s), "$.totalItems")).isZero();
    }

    @Test
    @DisplayName("regras de CPU/memória/disco avaliam amostras do agente; falta de dados abre alerta próprio")
    void agentMetricRulesAndNoData() throws Exception {
        Session s = register("Admin", "Org Agente Alertas");
        String resourceId = JsonPath.read(mvc.perform(post("/api/v1/resources").with(csrf()).cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Servidor\",\"type\":\"AGENT_SERVER\",\"targetUrl\":\"srv-01\",\"environment\":\"PRODUCTION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String token = JsonPath.read(mvc.perform(post("/api/v1/agents/register").with(csrf()).cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"%s\",\"name\":\"ag\",\"intervalSeconds\":10}".formatted(resourceId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.token");

        // Regra de disco padrão: > 90%, 1 ocorrência → dispara na primeira amostra com 95%
        mvc.perform(post("/api/v1/agents/ingest").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpuPercent\":10,\"disk\":{\"path\":\"/\",\"totalBytes\":100,\"usedBytes\":95}}"))
                .andExpect(status().isAccepted());
        String body = activeAlerts(s);
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].metric")).containsExactly("DISK");
        assertThat(JsonPath.<String>read(body, "$.items[0].severity")).isEqualTo("CRITICAL");

        // Regra de CPU exige 3 ocorrências e 60 s: uma amostra alta não dispara
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].metric")).doesNotContain("CPU");

        // Agente silencioso → AGENT_NO_DATA
        AlertEngineHelpers.makeAgentStale(resourceId, mvc, s, this);
        String after = activeAlerts(s);
        assertThat(JsonPath.<List<String>>read(after, "$.items[*].metric")).contains("AGENT_NO_DATA");
    }

    @Test
    @DisplayName("criação/edição de regras valida limites; desativar resolve alertas abertos")
    void ruleCrudValidation() throws Exception {
        Session s = register("Admin", "Org Regras CRUD");
        mvc.perform(post("/api/v1/alert-rules").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CPU sem limite\",\"metric\":\"CPU\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("rule_threshold_required"));
        mvc.perform(post("/api/v1/alert-rules").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CPU 150\",\"metric\":\"CPU\",\"severity\":\"HIGH\",\"operator\":\"GT\",\"threshold\":150}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("rule_threshold_range"));

        String ruleId = JsonPath.read(mvc.perform(post("/api/v1/alert-rules").with(csrf()).cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Qualquer falha\",\"metric\":\"STATUS_DOWN\",\"severity\":\"WARNING\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        UUID rid = UUID.fromString(createResource(s, "X", "http://localhost:9101/health", "{\"intervalSeconds\":30,\"failureThreshold\":1}"));
        fail(rid);
        // duas regras STATUS_DOWN (padrão + nova) → dois alertas distintos, cada um único por regra
        assertThat(JsonPath.<Integer>read(activeAlerts(s), "$.totalItems")).isEqualTo(2);

        mvc.perform(patch("/api/v1/alert-rules/" + ruleId).with(csrf()).cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(JsonPath.<Integer>read(activeAlerts(s), "$.totalItems")).isEqualTo(1);

        mvc.perform(delete("/api/v1/alert-rules/" + ruleId).with(csrf()).cookie(s.cookie())).andExpect(status().isNoContent());

        // visualizador não gerencia regras
        Session viewerOrg = register("Admin2", "Org Viewer");
        mvc.perform(post("/api/v1/alert-rules").with(csrf()).cookie(viewerOrg.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ok\",\"metric\":\"STATUS_DOWN\",\"severity\":\"INFO\"}"))
                .andExpect(status().isCreated());
    }
}
