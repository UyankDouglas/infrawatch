package com.infrawatch.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.monitoring.CheckOutcome;
import com.infrawatch.monitoring.CheckResultApplier;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Varredura de isolamento (Fase 6): a organização A cria um exemplar de cada entidade e a organização B tenta
 * ler, alterar e remover cada um por id. Toda rota com id precisa responder 404 (ou 403) — nunca 200/204 —
 * e toda listagem precisa vir vazia. A lista de rotas é explícita para que uma rota nova sem filtro de
 * organização quebre este teste.
 */
class IsolationSweepIT extends AbstractIntegrationTest {

    @Autowired CheckResultApplier applier;

    record Route(HttpMethod method, String path, String body) {}

    @Test
    @DisplayName("nenhuma rota por id de A responde com sucesso para B; listagens de B ficam vazias")
    void sweep() throws Exception {
        Session a = register("Admin A", "Org Isolamento A");
        Session b = register("Admin B", "Org Isolamento B");
        Map<String, String> ids = createEverything(a);

        List<Route> routes = new ArrayList<>();
        String r = ids.get("resource"), g = ids.get("group"), rule = ids.get("rule"), alert = ids.get("alert"),
                inc = ids.get("incident"), m = ids.get("maintenance"), ch = ids.get("channel"), exp = ids.get("export"),
                inv = ids.get("invite"), ag = ids.get("agent"), notif = ids.get("notification"), userA = a.userId().toString();
        // recursos
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r, null));
        routes.add(new Route(HttpMethod.PATCH, "/api/v1/resources/" + r, "{\"name\":\"hack\"}"));
        routes.add(new Route(HttpMethod.DELETE, "/api/v1/resources/" + r, null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/checks", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/history", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/metrics", null));
        routes.add(new Route(HttpMethod.POST, "/api/v1/resources/" + r + "/checks/run", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/alerts", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/incidents", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/maintenances", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/agent-metrics", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/resources/" + r + "/agent", null));
        routes.add(new Route(HttpMethod.GET, "/api/v1/reports/resources/" + r, null));
        // grupos
        routes.add(new Route(HttpMethod.PUT, "/api/v1/resource-groups/" + g, "{\"name\":\"hack\"}"));
        routes.add(new Route(HttpMethod.DELETE, "/api/v1/resource-groups/" + g, null));
        // alertas e regras
        routes.add(new Route(HttpMethod.GET, "/api/v1/alerts/" + alert, null));
        routes.add(new Route(HttpMethod.POST, "/api/v1/alerts/" + alert + "/acknowledge", "{}"));
        routes.add(new Route(HttpMethod.POST, "/api/v1/alerts/" + alert + "/resolve", "{}"));
        routes.add(new Route(HttpMethod.PATCH, "/api/v1/alert-rules/" + rule, "{\"enabled\":false}"));
        routes.add(new Route(HttpMethod.DELETE, "/api/v1/alert-rules/" + rule, null));
        // incidentes
        routes.add(new Route(HttpMethod.GET, "/api/v1/incidents/" + inc, null));
        routes.add(new Route(HttpMethod.PATCH, "/api/v1/incidents/" + inc, "{\"title\":\"hack\"}"));
        routes.add(new Route(HttpMethod.POST, "/api/v1/incidents/" + inc + "/status", "{\"status\":\"ACKNOWLEDGED\"}"));
        routes.add(new Route(HttpMethod.POST, "/api/v1/incidents/" + inc + "/comments", "{\"body\":\"hack\"}"));
        // manutenções
        routes.add(new Route(HttpMethod.GET, "/api/v1/maintenances/" + m, null));
        routes.add(new Route(HttpMethod.PATCH, "/api/v1/maintenances/" + m, "{\"title\":\"hack\"}"));
        routes.add(new Route(HttpMethod.POST, "/api/v1/maintenances/" + m + "/finish", null));
        // integrações
        routes.add(new Route(HttpMethod.PATCH, "/api/v1/integrations/channels/" + ch, "{\"enabled\":false}"));
        routes.add(new Route(HttpMethod.DELETE, "/api/v1/integrations/channels/" + ch, null));
        routes.add(new Route(HttpMethod.POST, "/api/v1/integrations/channels/" + ch + "/test", null));
        // relatórios
        routes.add(new Route(HttpMethod.GET, "/api/v1/reports/exports/" + exp + "/download", null));
        // membros e convites
        routes.add(new Route(HttpMethod.PATCH, "/api/v1/organizations/current/members/" + userA, "{\"role\":\"VIEWER\"}"));
        routes.add(new Route(HttpMethod.DELETE, "/api/v1/organizations/current/members/" + userA, null));
        routes.add(new Route(HttpMethod.DELETE, "/api/v1/organizations/current/invites/" + inv, null));
        // agentes
        routes.add(new Route(HttpMethod.POST, "/api/v1/agents/" + ag + "/rotate", null));
        routes.add(new Route(HttpMethod.PATCH, "/api/v1/agents/" + ag, "{\"intervalSeconds\":60}"));
        routes.add(new Route(HttpMethod.DELETE, "/api/v1/agents/" + ag, null));
        routes.add(new Route(HttpMethod.POST, "/api/v1/agents/register", "{\"resourceId\":\"" + r + "\",\"name\":\"hack\"}"));
        // notificações (por usuário)
        routes.add(new Route(HttpMethod.POST, "/api/v1/notifications/" + notif + "/read", null));
        // troca de organização para a de A
        routes.add(new Route(HttpMethod.POST, "/api/v1/organizations/" + a.organizationId() + "/switch", null));
        // criação apontando para entidades de A
        routes.add(new Route(HttpMethod.POST, "/api/v1/incidents", "{\"title\":\"x\",\"severity\":\"INFO\",\"resourceId\":\"" + r + "\"}"));
        routes.add(new Route(HttpMethod.POST, "/api/v1/maintenances", "{\"title\":\"x\",\"startsAt\":\"" + Instant.now().plusSeconds(600) + "\",\"endsAt\":\"" + Instant.now().plusSeconds(1200) + "\",\"resourceIds\":[\"" + r + "\"]}"));
        routes.add(new Route(HttpMethod.POST, "/api/v1/resources", "{\"name\":\"x\",\"type\":\"HTTP\",\"targetUrl\":\"http://localhost:9101/health\",\"environment\":\"DEVELOPMENT\",\"groupId\":\"" + g + "\",\"check\":{}}"));

        Map<String, Integer> leaks = new LinkedHashMap<>();
        for (Route route : routes) {
            var req = request(route.method(), route.path()).cookie(b.cookie());
            if (route.method() != HttpMethod.GET) req = req.with(csrf());
            if (route.body() != null) req = req.contentType(MediaType.APPLICATION_JSON).content(route.body());
            MvcResult res = mvc.perform(req).andReturn();
            int st = res.getResponse().getStatus();
            if (st < 400 || st >= 500) leaks.put(route.method() + " " + route.path(), st);
        }
        assertThat(leaks).as("rotas que responderam com sucesso (ou erro interno) para outra organização").isEmpty();

        // listagens de B não trazem nada de A
        for (String path : List.of("/api/v1/resources", "/api/v1/alerts", "/api/v1/incidents", "/api/v1/notifications",
                "/api/v1/integrations/deliveries")) {
            assertThat(JsonPath.<Integer>read(getJson(b, path), "$.totalItems")).as(path).isZero();
        }
        // auditoria de B contém apenas ações da própria B (o cadastro dela), nunca as de A
        List<String> auditUsers = JsonPath.read(getJson(b, "/api/v1/audit?size=100"), "$.items[*].userId");
        assertThat(auditUsers).isNotEmpty().allMatch(u -> u.equals(b.userId().toString()));
        for (String path : List.of("/api/v1/resource-groups", "/api/v1/maintenances", "/api/v1/integrations/channels",
                "/api/v1/reports/exports", "/api/v1/organizations/current/invites", "/api/v1/agents")) {
            assertThat(JsonPath.<Integer>read(getJson(b, path), "$.length()")).as(path).isZero();
        }
        assertThat(JsonPath.<Integer>read(getJson(b, "/api/v1/reports/availability"), "$.rows.length()")).isZero();
        assertThat(JsonPath.<Integer>read(getJson(b, "/api/v1/dashboard/overview"), "$.totalResources")).isZero();
        // as regras padrão de B são só as 7 dela
        assertThat(JsonPath.<Integer>read(getJson(b, "/api/v1/alert-rules"), "$.length()")).isEqualTo(7);
        // A continua íntegra
        mvc.perform(get("/api/v1/resources/" + r).cookie(a.cookie())).andExpect(status().isOk());
    }

    private String postJson(Session s, String path, String body) throws Exception {
        return mvc.perform(post(path).with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    private Map<String, String> createEverything(Session a) throws Exception {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("group", JsonPath.read(postJson(a, "/api/v1/resource-groups", "{\"name\":\"Grupo A\"}"), "$.id"));
        String rid = createResource(a, "Recurso A", "http://localhost:9101/health", "{\"intervalSeconds\":30,\"failureThreshold\":1,\"recoveryThreshold\":1}");
        ids.put("resource", rid);
        String server = JsonPath.read(postJson(a, "/api/v1/resources",
                "{\"name\":\"Servidor A\",\"type\":\"AGENT_SERVER\",\"targetUrl\":\"srv-a.interno\",\"environment\":\"DEVELOPMENT\"}"), "$.id");
        ids.put("agent", JsonPath.read(postJson(a, "/api/v1/agents/register", "{\"resourceId\":\"" + server + "\",\"name\":\"Agente A\"}"), "$.agent.id"));
        ids.put("rule", JsonPath.read(postJson(a, "/api/v1/alert-rules",
                "{\"name\":\"CPU alta A\",\"metric\":\"CPU\",\"severity\":\"HIGH\",\"operator\":\"GT\",\"threshold\":90}"), "$.id"));
        Instant now = Instant.now();
        applier.apply(UUID.fromString(rid), UUID.randomUUID(), new CheckOutcome(false, 503, 5, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(5)));
        ids.put("alert", JsonPath.read(getJson(a, "/api/v1/alerts?active=true&resourceId=" + rid), "$.items[0].id"));
        ids.put("incident", JsonPath.read(getJson(a, "/api/v1/incidents?active=true&resourceId=" + rid), "$.items[0].id"));
        ids.put("maintenance", JsonPath.read(postJson(a, "/api/v1/maintenances",
                "{\"title\":\"Janela A\",\"startsAt\":\"" + now.plusSeconds(3600) + "\",\"endsAt\":\"" + now.plusSeconds(7200) + "\",\"resourceIds\":[\"" + rid + "\"]}"), "$.id"));
        ids.put("channel", JsonPath.read(postJson(a, "/api/v1/integrations/channels",
                "{\"name\":\"Hook A\",\"type\":\"WEBHOOK\",\"webhookUrl\":\"http://localhost:9101/__webhook\"}"), "$.id"));
        ids.put("export", JsonPath.read(postJson(a, "/api/v1/reports/exports", "{\"type\":\"availability\",\"format\":\"csv\"}"), "$.id"));
        ids.put("invite", JsonPath.read(postJson(a, "/api/v1/organizations/current/invites", "{\"email\":\"conv-a@teste.local\",\"role\":\"VIEWER\"}"), "$.invite.id"));
        ids.put("notification", JsonPath.read(getJson(a, "/api/v1/notifications"), "$.items[0].id"));
        return ids;
    }
}
