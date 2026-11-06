package com.infrawatch.auth;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("cadastro cria usuário + organização e autentica a sessão")
    void registerCreatesUserAndOrganization() throws Exception {
        Session s = register("Maria Silva", "maria-" + System.nanoTime() + "@teste.local", "SenhaForte#2026", "ACME Ltda");
        mvc.perform(get("/api/v1/auth/me").session(s.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(s.email()))
                .andExpect(jsonPath("$.currentOrganization.name").value("ACME Ltda"))
                .andExpect(jsonPath("$.currentOrganization.role").value("ADMIN"))
                .andExpect(jsonPath("$.organizations", hasSize(1)));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        String email = "dup-" + System.nanoTime() + "@teste.local";
        register("Primeiro", email, "SenhaForte#2026", "Org A");
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Segundo\",\"email\":\"%s\",\"password\":\"SenhaForte#2026\",\"organizationName\":\"Org B\"}".formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void validatesRegistrationPayload() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"nao-e-email\",\"password\":\"curta\",\"organizationName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("login com senha errada retorna 401 sem detalhar o motivo")
    void loginWithWrongPassword() throws Exception {
        Session s = register("João", "Org J");
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"errada-errada\"}".formatted(s.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void loginIsCaseInsensitiveForEmail() throws Exception {
        String email = "case-" + System.nanoTime() + "@teste.local";
        register("Ana", email, "SenhaForte#2026", "Org Case");
        login(email.toUpperCase(), "SenhaForte#2026");
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        Session s = register("Carlos", "Org C");
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).session(s.http())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/resources").session(s.http())).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/resources")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mvc.perform(get("/api/v1/dashboard/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("mutations sem token CSRF são recusadas")
    void mutationsRequireCsrf() throws Exception {
        Session s = register("Bia", "Org B");
        mvc.perform(post("/api/v1/resources").session(s.http()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }
}
