package com.infrawatch.quality;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.support.AbstractIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Revisão de segurança (Fase 6): cabeçalhos, rotas públicas mínimas e mensagens de erro sem vazamento. */
class SecurityHeadersIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("toda resposta da API traz os cabeçalhos de segurança e nunca é cacheável")
    void securityHeaders() throws Exception {
        Session s = register("Admin", "Org Headers");
        mvc.perform(get("/api/v1/resources").cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("default-src 'none'")))
                .andExpect(header().string("Permissions-Policy", Matchers.containsString("camera=()")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(header().exists("X-Request-Id"));
        // mesmo em erro 401 os cabeçalhos estão presentes
        mvc.perform(get("/api/v1/resources"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    @DisplayName("rotas desconhecidas são negadas; erros não expõem stack trace nem detalhes internos")
    void unknownRoutesAndErrorShape() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/nao-existe")).andExpect(status().isUnauthorized());
        Session s = register("Admin", "Org Erros");
        mvc.perform(get("/api/v1/nao-existe").cookie(s.cookie())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("Exception"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
        // JSON malformado → 400 genérico, sem eco do corpo
        mvc.perform(post("/api/v1/resources").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("JsonParseException"))));
    }
}
