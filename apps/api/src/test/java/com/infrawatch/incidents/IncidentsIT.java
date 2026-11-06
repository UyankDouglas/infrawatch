package com.infrawatch.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.monitoring.CheckOutcome;
import com.infrawatch.monitoring.CheckResultApplier;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.organization.OrganizationMember;
import com.infrawatch.organization.OrganizationMemberRepository;
import com.infrawatch.organization.OrganizationRepository;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

class IncidentsIT extends AbstractIntegrationTest {

    @Autowired CheckResultApplier applier;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository members;
    @Autowired PasswordEncoder encoder;

    private void fail(UUID rid) {
        Instant now = Instant.now();
        applier.apply(rid, UUID.randomUUID(), new CheckOutcome(false, 503, 5, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(5)));
    }

    private void ok(UUID rid) {
        Instant now = Instant.now();
        applier.apply(rid, UUID.randomUUID(), new CheckOutcome(true, 200, 20, null, null, "127.0.0.1", now, now.plusMillis(20)));
    }

    @Test
    @DisplayName("indisponibilidade abre um único incidente automático vinculado ao alerta; recuperação o resolve; timeline preserva os eventos")
    void automaticIncidentLifecycle() throws Exception {
        Session s = register("Admin", "Org Inc Auto");
        UUID rid = UUID.fromString(createResource(s, "API", "http://localhost:9101/health", "{\"intervalSeconds\":30,\"failureThreshold\":2,\"recoveryThreshold\":1}"));
        ok(rid);
        fail(rid);
        fail(rid); // DOWN
        fail(rid); // continua DOWN: não duplica

        String list = getJson(s, "/api/v1/incidents?active=true");
        assertThat(JsonPath.<Integer>read(list, "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<String>read(list, "$.items[0].origin")).isEqualTo("AUTOMATIC");
        assertThat(JsonPath.<String>read(list, "$.items[0].severity")).isEqualTo("CRITICAL");
        assertThat(JsonPath.<Integer>read(list, "$.items[0].number")).isEqualTo(1);
        assertThat(JsonPath.<String>read(list, "$.items[0].alertEventId")).isNotNull();
        String id = JsonPath.read(list, "$.items[0].id");

        mvc.perform(get("/api/v1/notifications").cookie(s.cookie()))
                .andExpect(jsonPath("$.items[?(@.type=='INCIDENT_CREATED')]", hasSize(1)));
        mvc.perform(get("/api/v1/dashboard/overview").cookie(s.cookie()))
                .andExpect(jsonPath("$.activeIncidents").value(1))
                .andExpect(jsonPath("$.recentIncidents[0].id").value(id));

        ok(rid); // recovery → resolve automático
        String detail = getJson(s, "/api/v1/incidents/" + id);
        assertThat(JsonPath.<String>read(detail, "$.incident.status")).isEqualTo("RESOLVED");
        assertThat(JsonPath.<String>read(detail, "$.incident.cause")).contains("Recuperação");
        List<String> events = JsonPath.read(detail, "$.timeline[*].eventType");
        assertThat(events).contains("CREATED", "LINKED_ALERT", "AUTO_RESOLVED", "RESOURCE_STATUS");
        assertThat(JsonPath.<List<String>>read(detail, "$.allowedTransitions")).containsExactlyInAnyOrder("CLOSED", "INVESTIGATING");
    }

    @Test
    @DisplayName("incidente manual: ciclo completo, atribuição notifica o responsável, comentários e histórico; nunca resolve sozinho")
    void manualIncidentLifecycle() throws Exception {
        Session s = register("Admin", "Org Inc Manual");
        UUID rid = UUID.fromString(createResource(s, "API", "http://localhost:9101/health", "{\"intervalSeconds\":30,\"failureThreshold\":1,\"recoveryThreshold\":1}"));
        User op = users.save(new User("op-" + System.nanoTime() + "@teste.local", encoder.encode("SenhaForte#2026"), "Otávio"));
        members.save(new OrganizationMember(organizations.findById(s.organizationId()).orElseThrow(), op, MemberRole.OPERATOR));

        String created = mvc.perform(post("/api/v1/incidents").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lentidão relatada\",\"description\":\"Usuários relatam lentidão\",\"severity\":\"HIGH\",\"resourceId\":\"%s\",\"assigneeId\":\"%s\"}".formatted(rid, op.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.assignee.name").value("Otávio"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        Session opSession = login(op.getEmail(), "SenhaForte#2026");
        mvc.perform(get("/api/v1/notifications").cookie(opSession.cookie()))
                .andExpect(jsonPath("$.items[?(@.type=='INCIDENT_ASSIGNED')]", hasSize(1)));

        // Recuperação do recurso NÃO resolve incidente manual
        fail(rid);
        ok(rid);
        mvc.perform(get("/api/v1/incidents/" + id).cookie(s.cookie())).andExpect(jsonPath("$.incident.status").value("OPEN"));

        mvc.perform(post("/api/v1/incidents/" + id + "/comments").with(csrf()).cookie(opSession.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Analisando logs\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/incidents/" + id + "/status").with(csrf()).cookie(opSession.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledgedAt").exists());
        mvc.perform(post("/api/v1/incidents/" + id + "/status").with(csrf()).cookie(opSession.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/incidents/" + id + "/status").with(csrf()).cookie(opSession.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVESTIGATING\",\"note\":\"Causa provável: pool de conexões\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/incidents/" + id).with(csrf()).cookie(opSession.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cause\":\"Pool de conexões esgotado\",\"severity\":\"WARNING\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.severity").value("WARNING"));
        mvc.perform(post("/api/v1/incidents/" + id + "/status").with(csrf()).cookie(opSession.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolvedAt").exists()).andExpect(jsonPath("$.durationSeconds").isNumber());
        mvc.perform(post("/api/v1/incidents/" + id + "/status").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

        String detail = getJson(s, "/api/v1/incidents/" + id);
        List<String> events = JsonPath.read(detail, "$.timeline[*].eventType");
        assertThat(events).contains("CREATED", "ASSIGNED", "COMMENTED", "STATUS_CHANGED", "CAUSE_SET", "SEVERITY_CHANGED", "RESOURCE_STATUS");
        assertThat(JsonPath.<Integer>read(detail, "$.incident.commentCount")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(detail, "$.allowedTransitions")).isEmpty();

        mvc.perform(get("/api/v1/incidents/summary").cookie(s.cookie()))
                .andExpect(jsonPath("$.resolvedLast24h").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.mttrSeconds").isNumber());
        mvc.perform(get("/api/v1/audit").cookie(s.cookie()))
                .andExpect(jsonPath("$.items[*].action", hasItem("incident.status_changed")));
    }

    @Test
    @DisplayName("isolamento e RBAC: outra organização não vê; visualizador não cria nem comenta; recurso com auto_incident desligado não abre incidente")
    void isolationRbacAndOptOut() throws Exception {
        Session a = register("Admin A", "Org Inc A");
        Session b = register("Admin B", "Org Inc B");
        String id = JsonPath.read(mvc.perform(post("/api/v1/incidents").with(csrf()).cookie(a.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Só de A\",\"severity\":\"INFO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/incidents/" + id).cookie(b.cookie())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/incidents").cookie(b.cookie())).andExpect(jsonPath("$.totalItems").value(0));

        User viewer = users.save(new User("v-" + System.nanoTime() + "@teste.local", encoder.encode("SenhaForte#2026"), "Vera"));
        members.save(new OrganizationMember(organizations.findById(a.organizationId()).orElseThrow(), viewer, MemberRole.VIEWER));
        Session v = login(viewer.getEmail(), "SenhaForte#2026");
        mvc.perform(get("/api/v1/incidents/" + id).cookie(v.cookie())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/incidents").with(csrf()).cookie(v.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"severity\":\"INFO\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/incidents/" + id + "/comments").with(csrf()).cookie(v.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"x\"}")).andExpect(status().isForbidden());

        String rid = JsonPath.read(mvc.perform(post("/api/v1/resources").with(csrf()).cookie(a.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sem incidente\",\"type\":\"HTTP\",\"targetUrl\":\"http://localhost:9101/health\",\"environment\":\"DEVELOPMENT\",\"autoIncident\":false,\"check\":{\"intervalSeconds\":30,\"failureThreshold\":1}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.autoIncident").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");
        fail(UUID.fromString(rid));
        mvc.perform(get("/api/v1/incidents?active=true&resourceId=" + rid).cookie(a.cookie())).andExpect(jsonPath("$.totalItems").value(0));
        // mas o alerta de indisponibilidade continua existindo
        mvc.perform(get("/api/v1/alerts?active=true&resourceId=" + rid).cookie(a.cookie())).andExpect(jsonPath("$.totalItems").value(1));
    }
}
