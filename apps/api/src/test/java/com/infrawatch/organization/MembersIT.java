package com.infrawatch.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MembersIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("convite: token exibido uma vez, aceito por usuário novo (cadastro) e por usuário existente; e-mail precisa coincidir; revogação e expiração")
    void inviteLifecycle() throws Exception {
        Session admin = register("Admin", "Org Convites");
        String newEmail = "nova-" + UUID.randomUUID().toString().substring(0, 6) + "@teste.local";

        String created = mvc.perform(post("/api/v1/organizations/current/invites").with(csrf()).cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newEmail + "\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.startsWith("inv_")))
                .andExpect(jsonPath("$.link").value(org.hamcrest.Matchers.containsString("/convite/inv_")))
                .andExpect(jsonPath("$.invite.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(created, "$.token");

        // listagem nunca expõe o token
        String list = getJson(admin, "/api/v1/organizations/current/invites");
        assertThat(list).doesNotContain(token).contains("\"status\":\"PENDING\"");

        // público: descreve o convite
        mvc.perform(get("/api/v1/invites/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.organizationName").value("Org Convites"))
                .andExpect(jsonPath("$.roleLabel").value("Operador"));
        mvc.perform(get("/api/v1/invites/inv_inexistente")).andExpect(jsonPath("$.valid").value(false));

        // cadastro com e-mail diferente do convite é recusado
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Impostor\",\"email\":\"outro@teste.local\",\"password\":\"SenhaForte#2026\",\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest());

        // cadastro pelo convite entra na organização existente (sem criar outra)
        String me = mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nova Pessoa\",\"email\":\"" + newEmail + "\",\"password\":\"SenhaForte#2026\",\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentOrganization.id").value(admin.organizationId().toString()))
                .andExpect(jsonPath("$.currentOrganization.role").value("OPERATOR"))
                .andReturn().getResponse().getContentAsString();
        UUID newUserId = UUID.fromString(JsonPath.read(me, "$.id"));

        // token é de uso único
        mvc.perform(get("/api/v1/invites/" + token)).andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.reason").value("Convite já utilizado."));
        mvc.perform(get("/api/v1/organizations/current/members").cookie(admin.cookie()))
                .andExpect(jsonPath("$.length()").value(2));

        // usuário já existente aceita convite e passa a ter a organização como atual
        Session existing = register("Existente", "Org da Existente");
        String token2 = JsonPath.read(mvc.perform(post("/api/v1/organizations/current/invites").with(csrf()).cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + existing.email() + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/v1/invites/" + token2 + "/accept").with(csrf()).cookie(existing.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Org Convites"));
        mvc.perform(get("/api/v1/auth/me").cookie(existing.cookie()))
                .andExpect(jsonPath("$.currentOrganization.id").value(admin.organizationId().toString()))
                .andExpect(jsonPath("$.organizations.length()").value(2));

        // membro já existente não pode ser convidado de novo; revogação
        mvc.perform(post("/api/v1/organizations/current/invites").with(csrf()).cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + existing.email() + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
        String inv3 = mvc.perform(post("/api/v1/organizations/current/invites").with(csrf()).cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"revogado@teste.local\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/v1/organizations/current/invites/" + JsonPath.read(inv3, "$.invite.id")).with(csrf()).cookie(admin.cookie()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/invites/" + JsonPath.read(inv3, "$.token"))).andExpect(jsonPath("$.reason").value("Convite revogado."));

        // operador não convida (RBAC)
        Session op = login(newEmail, "SenhaForte#2026");
        mvc.perform(post("/api/v1/organizations/current/invites").with(csrf()).cookie(op.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@teste.local\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());

        // papel e remoção; último administrador protegido
        mvc.perform(patch("/api/v1/organizations/current/members/" + newUserId).with(csrf()).cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roleLabel").value("Administrador"));
        mvc.perform(patch("/api/v1/organizations/current/members/" + admin.userId()).with(csrf()).cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk()); // ainda há outro admin
        mvc.perform(delete("/api/v1/organizations/current/members/" + newUserId).with(csrf()).cookie(admin.cookie()))
                .andExpect(status().isForbidden()); // admin virou visualizador
        Session newAdmin = login(newEmail, "SenhaForte#2026");
        mvc.perform(delete("/api/v1/organizations/current/members/" + newUserId).with(csrf()).cookie(newAdmin.cookie()))
                .andExpect(status().isConflict()); // último admin
        mvc.perform(delete("/api/v1/organizations/current/members/" + admin.userId()).with(csrf()).cookie(newAdmin.cookie()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/audit?action=member.").cookie(newAdmin.cookie()))
                .andExpect(jsonPath("$.items[*].action", org.hamcrest.Matchers.hasItems("member.invited", "member.joined", "member.role_changed", "member.removed")))
                .andExpect(jsonPath("$.items[0].userName").isNotEmpty());
        mvc.perform(get("/api/v1/audit/actions").cookie(newAdmin.cookie()))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("member.invited")));
    }
}
