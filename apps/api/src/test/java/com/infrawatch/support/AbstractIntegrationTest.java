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
import org.springframework.mock.web.MockHttpSession;
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

    /** Sessão autenticada + identificadores úteis. */
    public record Session(MockHttpSession http, UUID userId, UUID organizationId, String email) {}

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
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        String body = result.getResponse().getContentAsString();
        UUID userId = UUID.fromString(JsonPath.read(body, "$.id"));
        UUID orgId = UUID.fromString(JsonPath.read(body, "$.currentOrganization.id"));
        return new Session(session, userId, orgId, email);
    }

    protected String createResource(Session session, String name, String url) throws Exception {
        return createResource(session, name, url, "{}");
    }

    protected String createResource(Session session, String name, String url, String checkJson) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/resources").with(csrf()).session(session.http())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"HTTP","targetUrl":"%s","environment":"DEVELOPMENT","check":%s}
                                """.formatted(name, url, checkJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected String getJson(Session session, String path) throws Exception {
        return mvc.perform(get(path).session(session.http()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
