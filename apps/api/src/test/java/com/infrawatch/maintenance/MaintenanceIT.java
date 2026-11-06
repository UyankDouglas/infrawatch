package com.infrawatch.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.monitoring.CheckOutcome;
import com.infrawatch.monitoring.CheckResultApplier;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class MaintenanceIT extends AbstractIntegrationTest {

    @Autowired CheckResultApplier applier;
    @Autowired MaintenanceService service;

    private void fail(UUID rid) {
        Instant now = Instant.now();
        applier.apply(rid, UUID.randomUUID(), new CheckOutcome(false, 503, 5, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(5)));
    }

    private void ok(UUID rid) {
        Instant now = Instant.now();
        applier.apply(rid, UUID.randomUUID(), new CheckOutcome(true, 200, 20, null, null, "127.0.0.1", now, now.plusMillis(20)));
    }

    private String statusOf(Session s, UUID rid) throws Exception {
        return JsonPath.read(getJson(s, "/api/v1/resources/" + rid), "$.status");
    }

    @Test
    @DisplayName("janela ativa coloca recursos em manutenção, resolve alertas, suprime alertas/incidentes e, ao encerrar, recalcula o estado")
    void maintenanceSuppressesAlertsAndIncidents() throws Exception {
        Session s = register("Admin", "Org Manut");
        UUID rid = UUID.fromString(createResource(s, "API", "http://localhost:9101/health", "{\"intervalSeconds\":30,\"failureThreshold\":1,\"recoveryThreshold\":1}"));
        ok(rid);
        fail(rid); // DOWN → alerta + incidente
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/alerts?active=true"), "$.totalItems")).isEqualTo(1);

        Instant now = Instant.now();
        String created = mvc.perform(post("/api/v1/maintenances").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Troca de disco\",\"reason\":\"Upgrade\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"resourceIds\":[\"%s\"]}"
                                .formatted(now.minusSeconds(5), now.plusSeconds(3600), rid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE")) // início no passado: ativa de imediato
                .andReturn().getResponse().getContentAsString();
        String windowId = JsonPath.read(created, "$.id");

        assertThat(statusOf(s, rid)).isEqualTo("MAINTENANCE");
        // alerta anterior resolvido pela manutenção
        String alerts = getJson(s, "/api/v1/alerts?active=false&resourceId=" + rid);
        assertThat(JsonPath.<String>read(alerts, "$.items[0].resolution")).isEqualTo("MAINTENANCE");
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/alerts?active=true"), "$.totalItems")).isZero();

        // Falhas durante a janela: verificação registrada, estado mantido, nenhum novo alerta/incidente
        long incidentsBefore = JsonPath.<Integer>read(getJson(s, "/api/v1/incidents"), "$.totalItems");
        fail(rid);
        fail(rid);
        assertThat(statusOf(s, rid)).isEqualTo("MAINTENANCE");
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/alerts?active=true"), "$.totalItems")).isZero();
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/incidents"), "$.totalItems")).isEqualTo((int) incidentsBefore);
        mvc.perform(get("/api/v1/resources/" + rid + "/checks").cookie(s.cookie()))
                .andExpect(jsonPath("$.items[0].success").value(false));

        // notificação de agendamento
        mvc.perform(get("/api/v1/notifications").cookie(s.cookie()))
                .andExpect(jsonPath("$.items[?(@.type=='MAINTENANCE_SCHEDULED')]", hasSize(1)));

        // dashboard: manutenção listada; disponibilidade exclui manutenção por padrão
        mvc.perform(get("/api/v1/dashboard/overview").cookie(s.cookie()).param("period", "1h"))
                .andExpect(jsonPath("$.upcomingMaintenances[0].id").value(windowId))
                .andExpect(jsonPath("$.maintenanceMode").value("excluded"));
        mvc.perform(get("/api/v1/resources/" + rid + "/metrics").cookie(s.cookie()).param("maintenance", "counted"))
                .andExpect(jsonPath("$.availabilityNote").value(org.hamcrest.Matchers.containsString("CONTADOS")));

        // encerrar antecipadamente → aguardando verificação; próxima verificação define o estado real
        mvc.perform(post("/api/v1/maintenances/" + windowId + "/finish").with(csrf()).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finishReason").value("ENDED_EARLY"));
        assertThat(statusOf(s, rid)).isEqualTo("PENDING");
        ok(rid);
        assertThat(statusOf(s, rid)).isEqualTo("UP");

        String history = getJson(s, "/api/v1/resources/" + rid + "/history");
        assertThat(JsonPath.<java.util.List<String>>read(history, "$.items[*].toStatus")).contains("MAINTENANCE", "PENDING", "UP");
    }

    @Test
    @DisplayName("agendamento futuro: validações, cancelamento, ativação e encerramento pelo agendador")
    void schedulingLifecycle() throws Exception {
        Session s = register("Admin", "Org Agenda");
        UUID rid = UUID.fromString(createResource(s, "API", "http://localhost:9101/health", "{\"intervalSeconds\":30}"));
        ok(rid);
        Instant now = Instant.now();

        mvc.perform(post("/api/v1/maintenances").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Inválida\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"resourceIds\":[\"%s\"]}".formatted(now.plusSeconds(600), now.plusSeconds(60), rid)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("maintenance_period"));
        mvc.perform(post("/api/v1/maintenances").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sem recursos\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"resourceIds\":[]}".formatted(now.plusSeconds(60), now.plusSeconds(600))))
                .andExpect(status().isBadRequest());

        String cancelId = JsonPath.read(mvc.perform(post("/api/v1/maintenances").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cancelar\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"resourceIds\":[\"%s\"]}".formatted(now.plusSeconds(3600), now.plusSeconds(7200), rid)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/maintenances/" + cancelId + "/finish").with(csrf()).cookie(s.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(statusOf(s, rid)).isEqualTo("UP");

        String id = JsonPath.read(mvc.perform(post("/api/v1/maintenances").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Curta\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"resourceIds\":[\"%s\"]}".formatted(now.plusSeconds(1), now.plusSeconds(2), rid)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        Thread.sleep(1200);
        assertThat(service.activateDue(Instant.now())).isEqualTo(1);
        assertThat(statusOf(s, rid)).isEqualTo("MAINTENANCE");
        Thread.sleep(1200);
        assertThat(service.completeDue(Instant.now())).isEqualTo(1);
        assertThat(statusOf(s, rid)).isEqualTo("PENDING");
        mvc.perform(get("/api/v1/maintenances/" + id).cookie(s.cookie()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finishReason").value("ENDED_ON_SCHEDULE"));

        Session other = register("Outra", "Org Agenda Outra");
        mvc.perform(get("/api/v1/maintenances/" + id).cookie(other.cookie())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/maintenances").cookie(other.cookie())).andExpect(jsonPath("$", hasSize(0)));
    }
}
