package com.infrawatch.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base para testes de integração: PostgreSQL real via Testcontainers, Flyway aplicado, MockMvc com sessão.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Contêiner singleton iniciado uma única vez por JVM. Não usamos @Testcontainers/@Container porque o
     * contexto Spring é cacheado entre as classes de teste e o contêiner seria parado ao fim da primeira classe.
     * O Ryuk do Testcontainers remove o contêiner quando a JVM termina.
     */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected org.springframework.context.ApplicationContext applicationContext;

    public <T> T bean(Class<T> type) {
        return applicationContext.getBean(type);
    }

    /** Nome do cookie de sessão (igual ao configurado em server.servlet.session.cookie.name). */
    public static final String SESSION_COOKIE = "INFRAWATCH_SESSION";

    /**
     * Sessão autenticada + identificadores úteis. Guardamos o cookie (não o MockHttpSession) porque, com o Spring
     * Session JDBC, a sessão real vive no banco e é identificada pelo cookie emitido na resposta.
     */
    public record Session(Cookie cookie, UUID userId, UUID organizationId, String email) {}

    protected Session register(String fullName, String organizationName) throws Exception {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.local";
        return register(fullName, email, "SenhaForte#2026", organizationName);
    }

    protected Session register(String fullName, String email, String password, String organizationName) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s","organizationName":"%s"}
                                """.formatted(fullName, email, password, organizationName)))
                .andExpect(status().isCreated())
                .andReturn();
        return toSession(result, email);
    }

    protected Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return toSession(result, email);
    }

    private Session toSession(MvcResult result, String email) throws Exception {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        if (cookie == null) throw new IllegalStateException("Resposta de autenticação sem cookie " + SESSION_COOKIE);
        String body = result.getResponse().getContentAsString();
        UUID userId = UUID.fromString(JsonPath.read(body, "$.id"));
        UUID orgId = UUID.fromString(JsonPath.read(body, "$.currentOrganization.id"));
        return new Session(new Cookie(SESSION_COOKIE, cookie.getValue()), userId, orgId, email);
    }

    protected String createResource(Session session, String name, String url) throws Exception {
        return createResource(session, name, url, "{}");
    }

    protected String createResource(Session session, String name, String url, String checkJson) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/resources").with(csrf()).cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"HTTP","targetUrl":"%s","environment":"DEVELOPMENT","check":%s}
                                """.formatted(name, url, checkJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected String getJson(Session session, String path) throws Exception {
        return mvc.perform(get(path).cookie(session.cookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
