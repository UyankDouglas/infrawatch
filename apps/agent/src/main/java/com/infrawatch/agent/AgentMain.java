package com.infrawatch.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * InfraWatch Agent — processo somente de saída.
 *
 * <ul>
 *   <li>Coleta métricas locais permitidas (CPU, memória, disco, uptime, carga) via {@link MetricsCollector}.</li>
 *   <li>Envia por HTTP(S) para {@code INFRAWATCH_API_URL/api/v1/agents/ingest} com token Bearer.</li>
 *   <li>Nunca abre portas, nunca executa comandos recebidos, nunca lê arquivos além dos de métricas.</li>
 * </ul>
 *
 * Configuração por variáveis de ambiente:
 * INFRAWATCH_API_URL, INFRAWATCH_AGENT_TOKEN, INFRAWATCH_AGENT_INTERVAL (s), INFRAWATCH_AGENT_DISK_PATH,
 * INFRAWATCH_AGENT_TIMEOUT_MS, INFRAWATCH_AGENT_HOSTNAME.
 */
public final class AgentMain {

    static final String VERSION = "0.2.0";

    private AgentMain() {}

    public static void main(String[] args) throws Exception {
        String apiUrl = require("INFRAWATCH_API_URL").replaceAll("/+$", "");
        String token = require("INFRAWATCH_AGENT_TOKEN");
        int interval = Integer.parseInt(env("INFRAWATCH_AGENT_INTERVAL", "30"));
        int timeoutMs = Integer.parseInt(env("INFRAWATCH_AGENT_TIMEOUT_MS", "5000"));
        String diskPath = env("INFRAWATCH_AGENT_DISK_PATH", "/");
        String hostname = env("INFRAWATCH_AGENT_HOSTNAME", MetricsCollector.defaultHostname());

        if (!apiUrl.startsWith("https://") && !isLocalOrPrivate(apiUrl)) {
            log("AVISO: a URL da API não usa HTTPS. Use TLS fora de redes locais/confiáveis.");
        }

        MetricsCollector collector = new MetricsCollector(diskPath);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        URI ingest = URI.create(apiUrl + "/api/v1/agents/ingest");

        log(String.format(Locale.ROOT, "InfraWatch Agent %s iniciado — host=%s intervalo=%ds destino=%s disco=%s",
                VERSION, hostname, interval, ingest, diskPath));

        // Primeira leitura de CPU serve de referência; a segunda já traz o percentual.
        collector.sample(hostname, VERSION);
        Thread.sleep(1_000);

        int effectiveInterval = interval;
        while (true) {
            long started = System.nanoTime();
            try {
                String body = collector.sample(hostname, VERSION);
                HttpRequest request = HttpRequest.newBuilder(ingest)
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "InfraWatch-Agent/" + VERSION)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 202) {
                    int suggested = parseInt(response.body(), "nextIntervalSeconds");
                    if (suggested >= 10 && suggested != effectiveInterval) {
                        log("Intervalo ajustado pelo servidor: " + suggested + "s");
                        effectiveInterval = suggested;
                    }
                    log("Amostra aceita (" + Instant.now() + ")");
                } else if (status == 401) {
                    log("ERRO: token recusado (401). Verifique INFRAWATCH_AGENT_TOKEN. Nova tentativa em " + effectiveInterval + "s.");
                } else if (status == 429) {
                    log("Servidor pediu menor frequência (429); aguardando o dobro do intervalo.");
                    Thread.sleep(effectiveInterval * 1000L);
                } else {
                    log("Resposta inesperada " + status + ": " + truncate(response.body()));
                }
            } catch (IOException e) {
                log("Falha de rede ao enviar amostra: " + e.getMessage());
            }
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            Thread.sleep(Math.max(1_000L, effectiveInterval * 1000L - elapsedMs));
        }
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            System.err.println("Variável de ambiente obrigatória ausente: " + name);
            System.exit(2);
        }
        return value.trim();
    }

    private static String env(String name, String def) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? def : value.trim();
    }

    private static boolean isLocalOrPrivate(String url) {
        String host = URI.create(url).getHost();
        return host != null && (host.equals("localhost") || host.equals("api") || host.endsWith(".local")
                || host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("172.")
                || host.equals("host.docker.internal") || host.equals("127.0.0.1"));
    }

    private static int parseInt(String json, String field) {
        int i = json.indexOf("\"" + field + "\"");
        if (i < 0) return -1;
        int colon = json.indexOf(':', i);
        int end = colon + 1;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == ' ')) end++;
        try {
            return Integer.parseInt(json.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) : s;
    }

    static void log(String message) {
        System.out.println("[infrawatch-agent] " + message);
    }
}
