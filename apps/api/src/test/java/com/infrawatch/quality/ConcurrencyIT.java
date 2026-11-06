package com.infrawatch.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.integrations.DeliveryDispatcher;
import com.infrawatch.integrations.NotificationDelivery;
import com.infrawatch.integrations.NotificationDeliveryRepository;
import com.infrawatch.monitoring.CheckOutcome;
import com.infrawatch.monitoring.CheckResultApplier;
import com.infrawatch.notifications.Notification;
import com.infrawatch.notifications.NotificationService;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Testes de concorrência (Fase 6): várias threads disputando o mesmo banco, como aconteceria com múltiplas
 * instâncias da API. Nada aqui usa mocks: PostgreSQL real, transações reais, bloqueios reais.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    @Autowired MonitoredResourceRepository resources;
    @Autowired CheckResultApplier applier;
    @Autowired NotificationService notifications;
    @Autowired DeliveryDispatcher dispatcher;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired PlatformTransactionManager txManager;

    static HttpServer server;
    static final AtomicInteger hits = new AtomicInteger();
    static final CopyOnWriteArrayList<String> deliveryIds = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", ex -> {
            hits.incrementAndGet();
            deliveryIds.add(ex.getRequestHeaders().getFirst("X-InfraWatch-Delivery"));
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() { server.stop(0); }

    /** Dispara N tarefas ao mesmo tempo (barreira) e devolve os resultados. */
    private static <T> List<T> runConcurrently(int threads, List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> t : tasks) futures.add(pool.submit(() -> { go.await(); return t.call(); }));
        go.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) out.add(f.get());
        pool.shutdown();
        return out;
    }

    @Test
    @DisplayName("claim do despachante: 8 despachantes simultâneos nunca reivindicam o mesmo recurso e nenhum recurso vencido fica para trás")
    void dispatcherClaimIsExclusive() throws Exception {
        Session s = register("Admin", "Org Concorrência Claim");
        Set<UUID> created = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 24; i++) {
            created.add(UUID.fromString(createResource(s, "R" + i, "http://localhost:9101/health", "{\"intervalSeconds\":30}")));
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        List<Callable<List<UUID>>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> tx.execute(st -> resources.claimDue(Instant.now(), 5)));
        List<List<UUID>> rounds = runConcurrently(8, tasks);

        List<UUID> all = rounds.stream().flatMap(List::stream).filter(created::contains).toList();
        assertThat(all).doesNotHaveDuplicates();
        // segunda rodada pega o restante; nada fica sem claim e nada é reclamado duas vezes (next_check_at avançou)
        List<UUID> rest = tx.execute(st -> resources.claimDue(Instant.now(), 100)).stream().filter(created::contains).toList();
        assertThat(rest).doesNotContainAnyElementsOf(all);
        assertThat(all.size() + rest.size()).isEqualTo(24);
        assertThat(tx.execute(st -> resources.claimDue(Instant.now(), 100)).stream().filter(created::contains)).isEmpty();
    }

    @Test
    @DisplayName("resultados simultâneos para o mesmo recurso: uma única transição para Indisponível, um alerta e um incidente")
    void concurrentOutcomesSameResource() throws Exception {
        Session s = register("Admin", "Org Concorrência Apply");
        UUID rid = UUID.fromString(createResource(s, "API", "http://localhost:9101/health",
                "{\"intervalSeconds\":30,\"failureThreshold\":2,\"recoveryThreshold\":1}"));
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tasks.add(() -> {
                Instant now = Instant.now();
                applier.apply(rid, UUID.randomUUID(), new CheckOutcome(false, 503, 3, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(3)));
                return true;
            });
        }
        assertThat(runConcurrently(16, tasks)).allMatch(Boolean::booleanValue);

        // Durante a disputa, resultados com início anterior ao último avaliado são ignorados (regra de fora de ordem),
        // então pode haver 0 ou 1 transição — nunca mais de uma, nunca alerta/incidente duplicado.
        List<String> during = JsonPath.read(getJson(s, "/api/v1/resources/" + rid + "/history"), "$.items[?(@.toStatus=='DOWN')].toStatus");
        assertThat(during).hasSizeLessThanOrEqualTo(1);
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/alerts?active=true&resourceId=" + rid), "$.totalItems")).isLessThanOrEqualTo(1);
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/incidents?active=true&resourceId=" + rid), "$.totalItems")).isLessThanOrEqualTo(1);

        // Depois da disputa, duas falhas ordenadas convergem para exatamente um DOWN, um alerta e um incidente.
        for (int i = 0; i < 2; i++) {
            Instant now = Instant.now();
            applier.apply(rid, UUID.randomUUID(), new CheckOutcome(false, 503, 3, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(3)));
            Thread.sleep(5);
        }
        List<String> to = JsonPath.read(getJson(s, "/api/v1/resources/" + rid + "/history"), "$.items[?(@.toStatus=='DOWN')].toStatus");
        assertThat(to).hasSize(1);
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/alerts?active=true&resourceId=" + rid), "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/incidents?active=true&resourceId=" + rid), "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(getJson(s, "/api/v1/resources/" + rid + "/checks"), "$.totalItems")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("outbox: 6 despachantes simultâneos entregam cada webhook exatamente uma vez")
    void outboxDeliversExactlyOnce() throws Exception {
        Session s = register("Admin", "Org Concorrência Outbox");
        mvc.perform(post("/api/v1/integrations/channels").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hook\",\"type\":\"WEBHOOK\",\"webhookUrl\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/hook\"}"));
        hits.set(0);
        deliveryIds.clear();
        int n = 30;
        for (int i = 0; i < n; i++) {
            notifications.notifyOrganization(s.organizationId(), Notification.Type.RESOURCE_DOWN, Severity.CRITICAL, "Evento " + i, "m", null, null, null, "k-" + UUID.randomUUID());
        }
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) tasks.add(() -> dispatcher.processDue(Instant.now(), 10));
        runConcurrently(6, tasks); // "handled" inclui entregas de outras organizações/testes, por isso não é afirmado
        // o agendador de fundo também pode ter processado parte; o invariante é "cada entrega uma vez"
        for (int i = 0; i < 50 && deliveries.countByOrganizationIdAndStatus(s.organizationId(), NotificationDelivery.Status.SENT) < n; i++) {
            dispatcher.processDue(Instant.now(), 10);
            Thread.sleep(100);
        }
        assertThat(deliveries.countByOrganizationIdAndStatus(s.organizationId(), NotificationDelivery.Status.SENT)).isEqualTo(n);
        assertThat(deliveries.countByOrganizationIdAndStatus(s.organizationId(), NotificationDelivery.Status.PENDING)).isZero();
        assertThat(hits.get()).isEqualTo(n);
        assertThat(deliveryIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("cadastro simultâneo com o mesmo e-mail: exatamente uma conta criada, as demais recebem 409")
    void concurrentRegisterSameEmail() throws Exception {
        String email = "corrida-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.local";
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int idx = i;
            tasks.add(() -> mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"P%d\",\"email\":\"%s\",\"password\":\"SenhaForte#2026\",\"organizationName\":\"Org Corrida %d\"}".formatted(idx, email, idx)))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(8, tasks);
        assertThat(Collections.frequency(statuses, 201)).isEqualTo(1);
        assertThat(statuses).allMatch(st -> st == 201 || st == 409);
    }
}
