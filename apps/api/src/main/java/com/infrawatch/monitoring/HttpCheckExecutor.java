package com.infrawatch.monitoring;

import com.infrawatch.resource.HealthCheckConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.net.ssl.SSLException;
import org.springframework.stereotype.Component;

/**
 * Executa a verificação HTTP de um recurso respeitando timeout, limite de bytes lidos,
 * número máximo de redirecionamentos (cada salto é revalidado pela política de alvos) e sem
 * seguir redirecionamentos automaticamente.
 */
@Component
public class HttpCheckExecutor {

    private final TargetValidator validator;
    private final MonitoringProperties properties;
    private final HttpClient client;

    public HttpCheckExecutor(TargetValidator validator, MonitoringProperties properties) {
        this.validator = validator;
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(Math.min(10_000, properties.maxTimeoutMs())))
                .build();
    }

    public CheckOutcome execute(String targetUrl, HealthCheckConfig config) {
        Instant started = Instant.now();
        StatusCodeMatcher matcher;
        try {
            matcher = new StatusCodeMatcher(config.getExpectedStatusCodes());
        } catch (IllegalArgumentException e) {
            return CheckOutcome.failure("config_invalid", e.getMessage(), null, started, null, null);
        }
        int timeoutMs = Math.min(config.getTimeoutMs(), properties.maxTimeoutMs());
        int maxHops = config.isFollowRedirects() ? properties.maxRedirects() : 0;

        URI current;
        try {
            current = validator.validateSyntax(targetUrl);
        } catch (TargetRejectedException e) {
            return CheckOutcome.failure(e.getCode(), e.getMessage(), null, started, null, null);
        }

        String resolved = null;
        for (int hop = 0; hop <= maxHops; hop++) {
            InetAddress address;
            try {
                address = validator.resolveAndValidate(current);
            } catch (TargetRejectedException e) {
                return CheckOutcome.failure(e.getCode(), e.getMessage(), resolved, started, null, null);
            }
            resolved = address.getHostAddress();

            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "InfraWatch-HealthCheck/1.0")
                    .header("Accept", "*/*")
                    .method(config.getHttpMethod().toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.noBody())
                    .build();

            long t0 = System.nanoTime();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpConnectTimeoutException e) {
                return CheckOutcome.failure("connect_timeout", "Tempo limite ao conectar.", resolved, started, null, elapsed(t0));
            } catch (HttpTimeoutException e) {
                return CheckOutcome.failure("timeout", "Tempo limite de resposta excedido (" + timeoutMs + " ms).", resolved, started, null, elapsed(t0));
            } catch (ConnectException e) {
                return CheckOutcome.failure("connection_refused", "Conexão recusada.", resolved, started, null, elapsed(t0));
            } catch (SSLException e) {
                return CheckOutcome.failure("tls_error", "Falha TLS: " + safe(e.getMessage()), resolved, started, null, elapsed(t0));
            } catch (IOException e) {
                return CheckOutcome.failure("io_error", "Erro de rede: " + safe(e.getMessage()), resolved, started, null, elapsed(t0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CheckOutcome.failure("interrupted", "Verificação interrompida.", resolved, started, null, elapsed(t0));
            }

            int responseTime = elapsed(t0);
            int status = response.statusCode();
            drain(response.body());

            if (isRedirect(status)) {
                if (hop == maxHops) {
                    boolean ok = matcher.matches(status);
                    return new CheckOutcome(ok, status, responseTime,
                            ok ? null : "unexpected_redirect",
                            ok ? null : "Redirecionamento (" + status + ") não é um código esperado.",
                            resolved, started, Instant.now());
                }
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    return CheckOutcome.failure("redirect_without_location", "Redirecionamento sem Location.", resolved, started, status, responseTime);
                }
                try {
                    current = validator.validateSyntax(current.resolve(location).toString());
                } catch (TargetRejectedException e) {
                    return CheckOutcome.failure(e.getCode(), "Redirecionamento recusado: " + e.getMessage(), resolved, started, status, responseTime);
                }
                continue;
            }

            boolean ok = matcher.matches(status);
            return new CheckOutcome(ok, status, responseTime,
                    ok ? null : "unexpected_status",
                    ok ? null : "Código HTTP " + status + " fora do esperado (" + config.getExpectedStatusCodes() + ").",
                    resolved, started, Instant.now());
        }
        return CheckOutcome.failure("too_many_redirects", "Número máximo de redirecionamentos excedido.", resolved, started, null, null);
    }

    private void drain(InputStream body) {
        if (body == null) return;
        try (body) {
            byte[] buf = new byte[4096];
            long total = 0;
            int n;
            while (total < properties.maxResponseBytes() && (n = body.read(buf)) != -1) {
                total += n;
            }
        } catch (IOException ignored) {
            // O corpo não é usado; falhas na leitura não alteram o resultado.
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static int elapsed(long t0) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - t0) / 1_000_000L);
    }

    private static String safe(String message) {
        if (message == null) return "sem detalhes";
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
