package com.infrawatch.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.notifications.Notification;
import com.infrawatch.notifications.NotificationService;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Canais externos: nada sai para a internet. O webhook aponta para um servidor HTTP iniciado dentro do teste e o
 * e-mail usa um JavaMailSender simulado (o SMTP real nunca é tocado).
 */
class IntegrationsIT extends AbstractIntegrationTest {

    record Received(String body, Map<String, String> headers) {}

    static HttpServer server;
    static final CopyOnWriteArrayList<Received> received = new CopyOnWriteArrayList<>();

    @MockitoBean JavaMailSender mailSender;
    @Autowired NotificationService notifications;
    @Autowired DeliveryDispatcher dispatcher;
    @Autowired NotificationDeliveryRepository deliveries;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> headers = Map.of(
                    "signature", String.valueOf(ex.getRequestHeaders().getFirst("X-InfraWatch-Signature")),
                    "event", String.valueOf(ex.getRequestHeaders().getFirst("X-InfraWatch-Event")),
                    "contentType", String.valueOf(ex.getRequestHeaders().getFirst("Content-Type")));
            received.add(new Received(body, headers));
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.createContext("/fail", ex -> { ex.sendResponseHeaders(500, -1); ex.close(); });
        server.start();
    }

    @AfterAll
    static void stopServer() { server.stop(0); }

    private String hookUrl(String path) { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }

    private String createChannel(Session s, String json) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/integrations/channels").with(csrf()).cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("webhook: destino fora da allowlist é recusado na criação (anti-SSRF) — nunca vira um proxy")
    void webhookTargetPolicy() throws Exception {
        Session s = register("Admin", "Org Hooks Policy");
        for (String bad : new String[]{"http://169.254.169.254/latest/meta-data", "http://10.0.0.5/hook", "https://exemplo-externo.com/hook", "ftp://localhost/x"}) {
            mvc.perform(post("/api/v1/integrations/channels").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ruim\",\"type\":\"WEBHOOK\",\"webhookUrl\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("recusado")));
        }
        mvc.perform(post("/api/v1/integrations/channels").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sem e-mail\",\"type\":\"EMAIL\",\"emailRecipients\":[\"não-é-email\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("webhook: entrega é enfileirada pelo outbox, assinada com HMAC-SHA256 e marcada como enviada; falhas reagendam com backoff")
    void webhookDeliveryWithSignature() throws Exception {
        Session s = register("Admin", "Org Hooks");
        String okId = createChannel(s, "{\"name\":\"Hook OK\",\"type\":\"WEBHOOK\",\"webhookUrl\":\"" + hookUrl("/hook")
                + "\",\"webhookSecret\":\"segredo-123\",\"minSeverity\":\"WARNING\"}");
        String failId = createChannel(s, "{\"name\":\"Hook Falha\",\"type\":\"WEBHOOK\",\"webhookUrl\":\"" + hookUrl("/fail") + "\"}");
        mvc.perform(get("/api/v1/integrations/channels").cookie(s.cookie()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].hasSecret").value(true))
                .andExpect(jsonPath("$[1].hasSecret").value(false));

        // INFO não atinge o mínimo (WARNING) do canal OK, mas atinge o canal de falha (INFO)
        notifications.notifyOrganization(s.organizationId(), Notification.Type.RESOURCE_NO_DATA, Severity.INFO, "Só informativo", "msg", null, null, null, "k-info-" + UUID.randomUUID());
        notifications.notifyOrganization(s.organizationId(), Notification.Type.RESOURCE_DOWN, Severity.CRITICAL, "API fora do ar", "Falhou 3x", "/recursos/x", null, null, "k-down-" + s.organizationId());
        // mesma chave → não duplica
        notifications.notifyOrganization(s.organizationId(), Notification.Type.RESOURCE_DOWN, Severity.CRITICAL, "API fora do ar", "Falhou 3x", "/recursos/x", null, null, "k-down-" + s.organizationId());

        dispatcher.processDue(Instant.now(), 50);
        for (int i = 0; i < 50 && received.stream().noneMatch(r -> r.body.contains("API fora do ar")); i++) Thread.sleep(100);
        Received hook = received.stream().filter(r -> r.body.contains("API fora do ar")).findFirst().orElseThrow();
        assertThat(hook.headers.get("event")).isEqualTo("RESOURCE_DOWN");
        assertThat(hook.headers.get("contentType")).startsWith("application/json");
        assertThat(hook.headers.get("signature")).isEqualTo(WebhookPayload.sign("segredo-123", hook.body));
        assertThat(hook.body).contains("\"link\":\"http://localhost:5174/recursos/x\"").contains("\"severity\":\"CRITICAL\"");
        assertThat(received.stream().filter(r -> r.body.contains("API fora do ar")).count()).isEqualTo(1);

        String list = getJson(s, "/api/v1/integrations/deliveries?channelId=" + okId);
        assertThat(JsonPath.<Integer>read(list, "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<String>read(list, "$.items[0].status")).isEqualTo("SENT");
        assertThat(JsonPath.<Integer>read(list, "$.items[0].responseCode")).isEqualTo(204);

        String failed = getJson(s, "/api/v1/integrations/deliveries?channelId=" + failId);
        assertThat(JsonPath.<Integer>read(failed, "$.totalItems")).isEqualTo(2);
        for (int i = 0; i < 2; i++) {
            assertThat(JsonPath.<String>read(failed, "$.items[" + i + "].status")).isEqualTo("PENDING");
            assertThat(JsonPath.<Integer>read(failed, "$.items[" + i + "].attempts")).isEqualTo(1);
            assertThat(JsonPath.<String>read(failed, "$.items[" + i + "].lastError")).isEqualTo("HTTP 500");
        }
        mvc.perform(get("/api/v1/integrations/channels").cookie(s.cookie()))
                .andExpect(jsonPath("$[1].lastError").value("HTTP 500"));

        // teste manual do canal
        mvc.perform(post("/api/v1/integrations/channels/" + okId + "/test").with(csrf()).cookie(s.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT")).andExpect(jsonPath("$.eventType").value("test"));

        mvc.perform(delete("/api/v1/integrations/channels/" + failId).with(csrf()).cookie(s.cookie())).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("e-mail: canal recebe a notificação pelo JavaMailSender; RBAC exige administrador")
    void emailDelivery() throws Exception {
        Session s = register("Admin", "Org Mail");
        createChannel(s, "{\"name\":\"Plantão\",\"type\":\"EMAIL\",\"emailRecipients\":[\"noc@teste.local\",\"ti@teste.local\"],\"eventTypes\":[\"INCIDENT_CREATED\"]}");
        notifications.notifyOrganization(s.organizationId(), Notification.Type.RESOURCE_DOWN, Severity.CRITICAL, "ignorado pelo filtro de tipo", "x", null, null, null, null);
        notifications.notifyOrganization(s.organizationId(), Notification.Type.INCIDENT_CREATED, Severity.HIGH, "Incidente #7 aberto", "Banco fora", "/incidentes/7", null, null, "k-" + UUID.randomUUID());
        dispatcher.processDue(Instant.now(), 50);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(TimeUnit.SECONDS.toMillis(5))).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("noc@teste.local", "ti@teste.local");
        assertThat(msg.getSubject()).isEqualTo("[InfraWatch][Alto] Incidente #7 aberto");
        assertThat(msg.getText()).contains("http://localhost:5174/incidentes/7");
        assertThat(deliveries.countByOrganizationIdAndStatus(s.organizationId(), NotificationDelivery.Status.SENT)).isEqualTo(1);

        Session viewerOrg = register("Outro", "Org Mail B");
        mvc.perform(get("/api/v1/integrations/deliveries").cookie(viewerOrg.cookie())).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("sem envio externo: entregas ficam como ignoradas e o teste do canal informa o motivo")
    void dispatchDisabledSkips() throws Exception {
        IntegrationsProperties disabled = new IntegrationsProperties(false, "http://localhost:5174", "x@localhost", 5, null, null);
        NotificationChannel c = new NotificationChannel(UUID.randomUUID(), "Hook", NotificationChannel.Type.WEBHOOK, null);
        c.setWebhookUrl(hookUrl("/hook"));
        NotificationDelivery d = new NotificationDelivery(c.getId(), c.getOrganizationId(), "test", Severity.INFO, "t", null, null, null, null, "{}");
        assertThat(disabled.dispatchEnabled()).isFalse();
        assertThat(disabled.expireAfter().toHours()).isEqualTo(6);
        d.failed(Instant.now(), "HTTP 500", 500, 5);
        assertThat(d.getStatus()).isEqualTo(NotificationDelivery.Status.PENDING);
        assertThat(d.getNextAttemptAt()).isAfter(Instant.now().plusSeconds(20));
        for (int i = 0; i < 4; i++) d.failed(Instant.now(), "HTTP 500", 500, 5);
        assertThat(d.getStatus()).isEqualTo(NotificationDelivery.Status.FAILED);
        assertThat(d.getAttempts()).isEqualTo(5);
    }
}
