package com.infrawatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.infrawatch.monitoring.CheckOutcome;
import com.infrawatch.monitoring.CheckResultApplier;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Type;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Sobe o servidor real (porta aleatória) para testar o canal WebSocket: handshake autenticado por cookie de
 * sessão, isolamento por organização e entrega de eventos após transições de estado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @LocalServerPort int port;
    @Autowired CheckResultApplier applier;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();

    record Account(String cookieHeader, UUID organizationId) {}

    private Account registerAccount(String org) throws Exception {
        String email = "ws-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.local";
        // CSRF: obtém cookie e envia no cabeçalho
        HttpClient client = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        java.net.CookieManager cm = (java.net.CookieManager) client.cookieHandler().orElseThrow();
        String xsrf = cm.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue).findFirst().orElseThrow();
        HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", xsrf)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"fullName":"WS","email":"%s","password":"SenhaForte#2026","organizationName":"%s"}
                        """.formatted(email, org))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(201);
        String cookies = cm.getCookieStore().getCookies().stream().map(c -> c.getName() + "=" + c.getValue())
                .reduce((a, b) -> a + "; " + b).orElseThrow();
        return new Account(cookies, UUID.fromString(JsonPath.read(res.body(), "$.currentOrganization.id")));
    }

    private UUID createResource(Account acc, String name) throws Exception {
        String xsrf = acc.cookieHeader().replaceAll(".*XSRF-TOKEN=([^;]+).*", "$1");
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/resources"))
                .header("Content-Type", "application/json").header("Cookie", acc.cookieHeader()).header("X-XSRF-TOKEN", xsrf)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"name":"%s","type":"HTTP","targetUrl":"http://localhost:9101/health","environment":"DEVELOPMENT","check":{"intervalSeconds":30,"failureThreshold":1}}
                        """.formatted(name))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as(res.body()).isEqualTo(201);
        return UUID.fromString(JsonPath.read(res.body(), "$.id"));
    }

    private StompSession connect(Account acc) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        // O servidor envia JSON; o conversor padrão de String só aceita text/plain.
        stomp.setMessageConverter(new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) {
                return true;
            }
        });
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", acc.cookieHeader());
        headers.add("Origin", "http://localhost:5174");
        return stomp.connectAsync("ws://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

    private BlockingQueue<String> subscribe(StompSession session, String destination) {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompSessionHandlerAdapter() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { queue.add(String.valueOf(payload)); }
        });
        return queue;
    }

    @Test
    @DisplayName("cliente autenticado recebe status_changed, check_completed, alert.triggered e notificação do próprio usuário")
    void receivesEventsForOwnOrganization() throws Exception {
        Account acc = registerAccount("Org WS");
        UUID rid = createResource(acc, "Serviço WS");
        StompSession session = connect(acc);
        BlockingQueue<String> org = subscribe(session, "/topic/org/" + acc.organizationId());
        BlockingQueue<String> user = subscribe(session, "/user/queue/notifications");
        Thread.sleep(300);

        Instant now = Instant.now();
        applier.apply(rid, UUID.randomUUID(), new CheckOutcome(false, 503, 5, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(5)));

        List<String> received = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String msg = org.poll(5, TimeUnit.SECONDS);
            if (msg != null) received.add(msg);
        }
        List<String> types = received.stream().map(m -> JsonPath.<String>read(m, "$.type")).toList();
        assertThat(types).contains("resource.status_changed", "resource.check_completed", "alert.triggered");
        String statusEvent = received.stream().filter(m -> m.contains("resource.status_changed")).findFirst().orElseThrow();
        assertThat(JsonPath.<String>read(statusEvent, "$.payload.toStatus")).isEqualTo("DOWN");
        assertThat(JsonPath.<String>read(statusEvent, "$.organizationId")).isEqualTo(acc.organizationId().toString());
        assertThat(JsonPath.<Integer>read(statusEvent, "$.seq")).isPositive();

        String notification = user.poll(5, TimeUnit.SECONDS);
        assertThat(notification).isNotNull();
        assertThat(JsonPath.<String>read(notification, "$.type")).isEqualTo("notification.created");
        session.disconnect();
    }

    @Test
    @DisplayName("assinar o tópico de outra organização é recusado e nenhum evento vaza")
    void cannotSubscribeToOtherOrganization() throws Exception {
        Account a = registerAccount("Org WS A");
        Account b = registerAccount("Org WS B");
        UUID rid = createResource(a, "Serviço de A");

        StompSession sessionB = connect(b);
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        BlockingQueue<String> leaked = new LinkedBlockingQueue<>();
        sessionB.subscribe("/topic/org/" + a.organizationId(), new StompSessionHandlerAdapter() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                if (StompCommand.ERROR.name().equals(headers.getFirst("command"))) errors.add(String.valueOf(payload));
                else leaked.add(String.valueOf(payload));
            }
        });
        Thread.sleep(300);
        Instant now = Instant.now();
        applier.apply(rid, UUID.randomUUID(), new CheckOutcome(false, 503, 5, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(5)));
        assertThat(leaked.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("handshake sem sessão é recusado")
    void unauthenticatedHandshakeIsRejected() {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Origin", "http://localhost:5174");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                stomp.connectAsync("ws://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
    }
}
