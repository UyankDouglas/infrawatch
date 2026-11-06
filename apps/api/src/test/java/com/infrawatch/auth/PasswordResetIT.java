package com.infrawatch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class PasswordResetIT extends AbstractIntegrationTest {

    @Autowired PasswordResetService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("recuperação de senha: resposta neutra, token de uso único com hash, senha antiga deixa de funcionar")
    void resetFlow() throws Exception {
        Session s = register("Pessoa", "Org Senha");
        mvc.perform(post("/api/v1/auth/forgot-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@teste.local\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Se existir")));

        // O link nunca sai pela API; aqui o obtemos direto do serviço (equivale ao e-mail/log).
        String link = service.requestReset(s.email()).orElseThrow();
        String token = link.substring(link.lastIndexOf('/') + 1);
        assertThat(token).startsWith("pwd_");
        Integer stored = jdbc.queryForObject("select count(*) from password_reset_tokens where token_hash = ?", Integer.class, token);
        assertThat(stored).isZero(); // apenas o hash é armazenado

        mvc.perform(post("/api/v1/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"curta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"NovaSenha#2026\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"Outra#Senha2026\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + s.email() + "\",\"password\":\"SenhaForte#2026\"}"))
                .andExpect(status().isUnauthorized());
        login(s.email(), "NovaSenha#2026");
    }

    @Test
    @DisplayName("sessões são persistidas em SPRING_SESSION (sobrevivem a reinício da API)")
    void sessionsArePersistedInDatabase() throws Exception {
        Session s = register("Pessoa", "Org Sessão");
        Integer before = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
        assertThat(before).isGreaterThanOrEqualTo(1);
        // A sessão do login acima foi gravada pelo SessionRepositoryFilter; abaixo, verificação direta do repositório.
        var repo = bean(org.springframework.session.SessionRepository.class);
        var created = repo.createSession();
        created.setAttribute("prova", s.userId().toString());
        repo.save(created);
        var loaded = repo.findById(created.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.<String>getAttribute("prova")).isEqualTo(s.userId().toString());
        Integer rows = jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class, created.getId());
        assertThat(rows).isEqualTo(1);
    }
}
