package com.infrawatch.resource;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.organization.MemberRole;
import com.infrawatch.organization.Organization;
import com.infrawatch.organization.OrganizationMember;
import com.infrawatch.organization.OrganizationMemberRepository;
import com.infrawatch.organization.OrganizationRepository;
import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

class ResourceApiIT extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository members;
    @Autowired PasswordEncoder encoder;

    @Test
    @DisplayName("CRUD completo de recurso com configuração de verificação")
    void crud() throws Exception {
        Session s = register("Admin", "Org CRUD");
        String id = createResource(s, "Serviço A", "http://localhost:9101/health",
                "{\"intervalSeconds\":60,\"timeoutMs\":2000,\"degradedLatencyMs\":500,\"failureThreshold\":2}");

        mvc.perform(get("/api/v1/resources/" + id).session(s.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Serviço A"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusLabel").value("Aguardando verificação"))
                .andExpect(jsonPath("$.check.intervalSeconds").value(60))
                .andExpect(jsonPath("$.check.failureThreshold").value(2))
                .andExpect(jsonPath("$.check.degradedLatencyMs").value(500));

        mvc.perform(patch("/api/v1/resources/" + id).with(csrf()).session(s.http())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Serviço A2\",\"tags\":[\"Core\",\"api\"],\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Serviço A2"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.tags[0]").value("core"));

        mvc.perform(get("/api/v1/resources").session(s.http()).param("search", "a2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalItems").value(1));

        mvc.perform(delete("/api/v1/resources/" + id).with(csrf()).session(s.http())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/resources/" + id).session(s.http())).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/audit").session(s.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("resource.deleted"));
    }

    @Test
    void rejectsDuplicateNameInSameOrganization() throws Exception {
        Session s = register("Admin", "Org Dup");
        createResource(s, "Mesmo nome", "http://localhost:9101/health");
        mvc.perform(post("/api/v1/resources").with(csrf()).session(s.http()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mesmo NOME\",\"type\":\"HTTP\",\"targetUrl\":\"http://localhost:9102/health\",\"environment\":\"DEVELOPMENT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("destinos fora da allowlist, metadados de nuvem e esquemas inválidos são recusados")
    void rejectsUnsafeTargets() throws Exception {
        Session s = register("Admin", "Org SSRF");
        String[][] cases = {
                {"https://example.com/health", "target_not_allowlisted"},
                {"http://169.254.169.254/latest/meta-data", "target_not_allowlisted"},
                {"http://10.0.0.1/", "target_not_allowlisted"},
                {"ftp://localhost/", "target_scheme"},
                {"http://user:senha@localhost/", "target_userinfo"},
        };
        for (String[] c : cases) {
            mvc.perform(post("/api/v1/resources").with(csrf()).session(s.http()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X %s\",\"type\":\"HTTP\",\"targetUrl\":\"%s\",\"environment\":\"DEVELOPMENT\"}"
                                    .formatted(c[1], c[0])))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(c[1]));
        }
    }

    @Test
    void validatesCheckConfiguration() throws Exception {
        Session s = register("Admin", "Org Val");
        mvc.perform(post("/api/v1/resources").with(csrf()).session(s.http()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rápido\",\"type\":\"HTTP\",\"targetUrl\":\"http://localhost:9101/\",\"environment\":\"DEVELOPMENT\",\"check\":{\"intervalSeconds\":5}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['check.intervalSeconds']").exists());

        mvc.perform(post("/api/v1/resources").with(csrf()).session(s.http()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Timeout\",\"type\":\"HTTP\",\"targetUrl\":\"http://localhost:9101/\",\"environment\":\"DEVELOPMENT\",\"check\":{\"intervalSeconds\":30,\"timeoutMs\":30000}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("timeout_vs_interval"));

        mvc.perform(post("/api/v1/resources").with(csrf()).session(s.http()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HTTPS\",\"type\":\"HTTPS\",\"targetUrl\":\"http://localhost:9101/\",\"environment\":\"DEVELOPMENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("target_scheme"));
    }

    @Test
    @DisplayName("isolamento: organizações distintas não enxergam recursos umas das outras")
    void organizationsAreIsolated() throws Exception {
        Session a = register("Admin A", "Org Isolada A");
        Session b = register("Admin B", "Org Isolada B");
        String id = createResource(a, "Recurso de A", "http://localhost:9101/health");

        mvc.perform(get("/api/v1/resources").session(b.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/resources/" + id).session(b.http())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/resources/" + id + "/checks").session(b.http())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/resources/" + id + "/history").session(b.http())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/resources/" + id).with(csrf()).session(b.http())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"hack\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/resources/" + id).with(csrf()).session(b.http())).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/dashboard/overview").session(b.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResources").value(0));

        // B não consegue "trocar" para a organização de A
        mvc.perform(post("/api/v1/organizations/" + a.organizationId() + "/switch").with(csrf()).session(b.http()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: visualizador e operador não cadastram recursos; operador pode verificar agora")
    void rbacIsEnforced() throws Exception {
        Session admin = register("Admin", "Org RBAC");
        String hash = encoder.encode("SenhaForte#2026");
        Organization org = organizations.findById(admin.organizationId()).orElseThrow();
        User viewer = users.save(new User("viewer-" + System.nanoTime() + "@teste.local", hash, "Vera"));
        User operator = users.save(new User("op-" + System.nanoTime() + "@teste.local", hash, "Otávio"));
        members.save(new OrganizationMember(org, viewer, MemberRole.VIEWER));
        members.save(new OrganizationMember(org, operator, MemberRole.OPERATOR));

        Session v = login(viewer.getEmail(), "SenhaForte#2026");
        Session o = login(operator.getEmail(), "SenhaForte#2026");
        String body = "{\"name\":\"Novo\",\"type\":\"HTTP\",\"targetUrl\":\"http://localhost:9101/\",\"environment\":\"DEVELOPMENT\"}";

        mvc.perform(post("/api/v1/resources").with(csrf()).session(v.http()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/resources").with(csrf()).session(o.http()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").session(v.http())).andExpect(status().isForbidden());

        String id = createResource(admin, "Recurso", "http://localhost:1/health");
        mvc.perform(get("/api/v1/resources/" + id).session(v.http())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/resources/" + id + "/checks/run").with(csrf()).session(v.http())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/resources/" + id + "/checks/run").with(csrf()).session(o.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executed").value(true))
                .andExpect(jsonPath("$.execution.success").value(false));

        mvc.perform(get("/api/v1/organizations/current/members").session(v.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }
}
