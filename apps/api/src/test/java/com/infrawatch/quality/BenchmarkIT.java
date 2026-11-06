package com.infrawatch.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Benchmark reproduzível (Fase 6). Não roda na suíte normal: {@code ./mvnw test -Dtest=BenchmarkIT -Dinfrawatch.bench=true}.
 * Popula uma organização com muitos recursos, execuções e transições e mede claim do despachante, relatórios e
 * dashboard. Resultado impresso e gravado em target/benchmark.md (copiado para docs/benchmark.md).
 */
@EnabledIfSystemProperty(named = "infrawatch.bench", matches = "true")
class BenchmarkIT extends AbstractIntegrationTest {

    static final int RESOURCES = Integer.getInteger("bench.resources", 300);
    static final int EXECUTIONS = Integer.getInteger("bench.executions", 200_000);
    static final int TRANSITIONS = Integer.getInteger("bench.transitions", 20_000);
    static final int INCIDENTS = Integer.getInteger("bench.incidents", 2_000);

    @Autowired JdbcTemplate jdbc;
    @Autowired MonitoredResourceRepository resources;
    @Autowired PlatformTransactionManager txManager;

    record Measure(String name, double p50, double p95, double max, int runs) {}

    @Test
    @DisplayName("benchmark: seed sintético + medições de claim, relatórios e dashboard")
    void benchmark() throws Exception {
        Session s = register("Bench", "Org Benchmark");
        UUID org = s.organizationId();
        Random rnd = new Random(42);
        Instant now = Instant.now();

        long t0 = System.nanoTime();
        List<UUID> ids = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        List<Object[]> cfgs = new ArrayList<>();
        for (int i = 0; i < RESOURCES; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            rows.add(new Object[]{id, org, "Recurso " + i, "HTTP", "http://localhost:9101/health?i=" + i, "PRODUCTION", "UP", Timestamp.from(now.minusSeconds(86400L * 30))});
            cfgs.add(new Object[]{UUID.randomUUID(), id, 60});
        }
        jdbc.batchUpdate("INSERT INTO monitored_resources (id, organization_id, name, type, target_url, environment, current_status, created_at, status_since) VALUES (?,?,?,?,?,?,?,?,?)",
                rows.stream().map(r -> new Object[]{r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[7]}).toList());
        jdbc.batchUpdate("INSERT INTO health_check_configs (id, resource_id, interval_seconds) VALUES (?,?,?)", cfgs);

        List<Object[]> exec = new ArrayList<>(5000);
        for (int i = 0; i < EXECUTIONS; i++) {
            UUID rid = ids.get(rnd.nextInt(ids.size()));
            Instant at = now.minusSeconds(rnd.nextInt(86400 * 30));
            boolean ok = rnd.nextInt(100) < 97;
            exec.add(new Object[]{UUID.randomUUID(), rid, org, UUID.randomUUID(), "HTTP", Timestamp.from(at), Timestamp.from(at.plusMillis(50)), 50, ok,
                    ok ? 200 : 503, ok ? 20 + rnd.nextInt(400) : null, ok ? null : "unexpected_status"});
            if (exec.size() == 5000) { flushExec(exec); exec.clear(); }
        }
        flushExec(exec);

        List<Object[]> hist = new ArrayList<>();
        for (int i = 0; i < TRANSITIONS; i++) {
            UUID rid = ids.get(rnd.nextInt(ids.size()));
            Instant at = now.minusSeconds(rnd.nextInt(86400 * 30));
            boolean down = rnd.nextBoolean();
            hist.add(new Object[]{UUID.randomUUID(), rid, org, down ? "UP" : "DOWN", down ? "DOWN" : "UP", "bench", Timestamp.from(at)});
        }
        jdbc.batchUpdate("INSERT INTO resource_status_history (id, resource_id, organization_id, from_status, to_status, reason, changed_at) VALUES (?,?,?,?,?,?,?)", hist);

        List<Object[]> inc = new ArrayList<>();
        for (int i = 0; i < INCIDENTS; i++) {
            UUID rid = ids.get(rnd.nextInt(ids.size()));
            Instant opened = now.minusSeconds(rnd.nextInt(86400 * 30));
            Instant resolved = opened.plusSeconds(60 + rnd.nextInt(7200));
            inc.add(new Object[]{UUID.randomUUID(), org, i + 1, rid, "Incidente " + i, "CRITICAL", "RESOLVED", "AUTOMATIC",
                    Timestamp.from(opened), Timestamp.from(resolved), Timestamp.from(opened), Timestamp.from(resolved)});
        }
        jdbc.batchUpdate("INSERT INTO incidents (id, organization_id, number, resource_id, title, severity, status, origin, opened_at, resolved_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", inc);
        jdbc.execute("ANALYZE");
        double seedSeconds = (System.nanoTime() - t0) / 1e9;

        TransactionTemplate tx = new TransactionTemplate(txManager);
        List<Measure> measures = new ArrayList<>();
        measures.add(measure("claimDue(50) com " + RESOURCES + " recursos vencidos", 30, () -> {
            tx.execute(st -> resources.claimDue(Instant.now(), 50));
            jdbc.update("UPDATE monitored_resources SET next_check_at = now() WHERE organization_id = ?", org); // rearma
            return null;
        }));
        measures.add(measure("GET /reports/availability?period=30d", 10, () -> perform(s, "/api/v1/reports/availability?period=30d")));
        measures.add(measure("GET /reports/availability?period=7d", 10, () -> perform(s, "/api/v1/reports/availability?period=7d")));
        measures.add(measure("GET /reports/incidents?period=30d", 10, () -> perform(s, "/api/v1/reports/incidents?period=30d")));
        measures.add(measure("GET /dashboard/overview?period=24h", 10, () -> perform(s, "/api/v1/dashboard/overview?period=24h")));
        measures.add(measure("GET /dashboard/availability?period=7d", 10, () -> perform(s, "/api/v1/dashboard/availability?period=7d")));
        measures.add(measure("GET /resources?page=0&size=50", 10, () -> perform(s, "/api/v1/resources?page=0&size=50")));
        measures.add(measure("GET /resources/{id}/metrics?period=30d", 10, () -> perform(s, "/api/v1/resources/" + ids.getFirst() + "/metrics?period=30d")));
        measures.add(measure("GET /reports/resources/{id}?period=30d", 10, () -> perform(s, "/api/v1/reports/resources/" + ids.getFirst() + "?period=30d")));

        StringBuilder md = new StringBuilder();
        md.append("| Operação | p50 (ms) | p95 (ms) | máx (ms) | execuções |\n|---|---:|---:|---:|---:|\n");
        for (Measure m : measures) md.append(String.format(java.util.Locale.ROOT, "| %s | %.1f | %.1f | %.1f | %d |%n", m.name(), m.p50(), m.p95(), m.max(), m.runs()));
        String header = String.format(java.util.Locale.ROOT, "Seed: %d recursos, %d execuções, %d transições, %d incidentes em %.1f s%n%n", RESOURCES, EXECUTIONS, TRANSITIONS, INCIDENTS, seedSeconds);
        System.out.println("\n===== BENCHMARK =====\n" + header + md);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/benchmark.md"), header + md);
        assertThat(measures).allSatisfy(m -> assertThat(m.p95()).as(m.name()).isLessThan(5000));
    }

    private void flushExec(List<Object[]> exec) {
        jdbc.batchUpdate("INSERT INTO health_check_executions (id, resource_id, organization_id, run_id, check_type, started_at, finished_at, duration_ms, success, http_status, response_time_ms, error_code) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", exec);
    }

    private Object perform(Session s, String path) {
        try {
            mvc.perform(get(path).cookie(s.cookie())).andExpect(status().isOk());
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Measure measure(String name, int runs, Supplier<Object> op) {
        op.get(); // aquecimento
        double[] samples = new double[runs];
        for (int i = 0; i < runs; i++) {
            long t = System.nanoTime();
            op.get();
            samples[i] = (System.nanoTime() - t) / 1e6;
        }
        java.util.Arrays.sort(samples);
        int p95 = Math.max(0, Math.min(runs - 1, (int) Math.ceil(runs * 0.95) - 1));
        return new Measure(name, samples[runs / 2], samples[p95], samples[runs - 1], runs);
    }
}
