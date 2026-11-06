package com.infrawatch.integrations;

import com.infrawatch.monitoring.TargetRejectedException;
import com.infrawatch.monitoring.TargetValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Processa o outbox de entregas externas: e-mail via SMTP e webhooks HTTP(S) assinados.
 * Desativado por padrão em desenvolvimento ({@code infrawatch.integrations.dispatch-enabled=false}): as entregas
 * ficam registradas como SKIPPED e nada sai da máquina. Retentativas com backoff exponencial; entregas antigas expiram.
 */
@Component
public class DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);
    /** Prazo de reserva de uma entrega reivindicada (ver processDue). */
    static final Duration LEASE = Duration.ofSeconds(60);

    private final NotificationDeliveryRepository deliveries;
    private final NotificationChannelRepository channels;
    private final IntegrationsProperties properties;
    private final TargetValidator targetValidator;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final TransactionTemplate tx;
    private final HttpClient http;
    private final Counter sent;
    private final Counter failed;

    public DeliveryDispatcher(NotificationDeliveryRepository deliveries, NotificationChannelRepository channels,
                              IntegrationsProperties properties, TargetValidator targetValidator,
                              ObjectProvider<JavaMailSender> mailSender, PlatformTransactionManager txManager,
                              MeterRegistry registry) {
        this.deliveries = deliveries;
        this.channels = channels;
        this.properties = properties;
        this.targetValidator = targetValidator;
        this.mailSender = mailSender;
        this.tx = new TransactionTemplate(txManager);
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.webhookTimeout()).build();
        this.sent = Counter.builder("infrawatch.engine.deliveries.sent").register(registry);
        this.failed = Counter.builder("infrawatch.engine.deliveries.failed").register(registry);
    }

    @Scheduled(fixedDelayString = "10s", initialDelayString = "15s")
    public void dispatch() {
        processDue(Instant.now(), 50);
    }

    /** Processa até {@code limit} entregas vencidas. Retorna quantas foram tratadas. */
    public int processDue(Instant now, int limit) {
        List<NotificationDelivery> batch = tx.execute(status -> deliveries.claimDue(now, limit).stream().map(d -> {
            // Decide dentro da transação de claim para evitar reprocessamento concorrente.
            if (!properties.dispatchEnabled()) {
                d.skipped("Envio externo desativado (infrawatch.integrations.dispatch-enabled=false)");
                return d;
            }
            if (d.getCreatedAt().isBefore(now.minus(properties.expireAfter()))) {
                d.expired();
                return d;
            }
            // Lease: entre o commit do claim e a gravação do resultado a linha continuaria PENDING e vencida, e outro
            // despachante (outra instância ou o agendador) poderia reivindicá-la de novo. Adiar next_attempt_at fecha
            // essa janela; se o processo cair no meio do envio, a entrega volta a ser elegível após o prazo.
            d.lease(now.plus(LEASE));
            return d;
        }).toList());
        if (batch == null || batch.isEmpty()) return 0;

        int handled = 0;
        for (NotificationDelivery d : batch) {
            if (d.getStatus() != NotificationDelivery.Status.PENDING) { handled++; continue; }
            Optional<NotificationChannel> channel = channels.findById(d.getChannelId());
            Result result = channel.map(c -> send(c, d)).orElse(new Result(false, null, "Canal removido"));
            tx.executeWithoutResult(s -> deliveries.findById(d.getId()).ifPresent(fresh -> {
                if (result.ok()) { fresh.sent(Instant.now(), result.code()); sent.increment(); }
                else { fresh.failed(Instant.now(), result.error(), result.code(), properties.maxAttempts()); failed.increment(); }
                channel.flatMap(c -> channels.findById(c.getId())).ifPresent(c -> c.recordDelivery(Instant.now(), result.ok() ? null : result.error()));
            }));
            handled++;
        }
        return handled;
    }

    record Result(boolean ok, Integer code, String error) {}

    Result send(NotificationChannel channel, NotificationDelivery d) {
        try {
            return switch (channel.getType()) {
                case EMAIL -> sendEmail(channel, d);
                case WEBHOOK -> sendWebhook(channel, d);
            };
        } catch (Exception e) {
            log.warn("Entrega {} falhou no canal {}: {}", d.getId(), channel.getName(), e.toString());
            return new Result(false, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Result sendEmail(NotificationChannel channel, NotificationDelivery d) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) return new Result(false, null, "SMTP não configurado (spring.mail.host)");
        String[] to = channel.getEmailRecipients() == null ? new String[0]
                : channel.getEmailRecipients().split("[,;\\s]+");
        if (to.length == 0 || to[0].isBlank()) return new Result(false, null, "Canal sem destinatários");
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(properties.fromAddress());
        msg.setTo(to);
        msg.setSubject("[InfraWatch][" + d.getSeverity().getLabel() + "] " + d.getTitle());
        msg.setText((d.getMessage() == null ? "" : d.getMessage() + "\n\n")
                + (d.getLink() == null ? "" : "Abrir no InfraWatch: " + properties.baseUrl() + d.getLink() + "\n\n")
                + "Evento: " + d.getEventType() + "\nSeveridade: " + d.getSeverity().getLabel() + "\n"
                + "— InfraWatch");
        sender.send(msg);
        return new Result(true, null, null);
    }

    private Result sendWebhook(NotificationChannel channel, NotificationDelivery d) throws Exception {
        URI uri;
        try {
            uri = targetValidator.validateSyntax(channel.getWebhookUrl());
            targetValidator.resolveAndValidate(uri); // mesma política anti-SSRF das verificações
        } catch (TargetRejectedException e) {
            return new Result(false, null, "Destino recusado: " + e.getMessage());
        }
        String body = d.getPayload();
        HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                .timeout(properties.webhookTimeout())
                .header("Content-Type", "application/json")
                .header("User-Agent", "InfraWatch-Webhook/1.0")
                .header("X-InfraWatch-Event", d.getEventType())
                .header("X-InfraWatch-Delivery", d.getId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (channel.getWebhookSecret() != null && !channel.getWebhookSecret().isBlank()) {
            req.header("X-InfraWatch-Signature", WebhookPayload.sign(channel.getWebhookSecret(), body));
        }
        HttpResponse<Void> res = http.send(req.build(), HttpResponse.BodyHandlers.discarding());
        int code = res.statusCode();
        return code >= 200 && code < 300 ? new Result(true, code, null) : new Result(false, code, "HTTP " + code);
    }

    static Duration backoff(int attempt) {
        return Duration.ofSeconds((long) Math.min(3600, 30 * Math.pow(2, Math.max(0, attempt - 1))));
    }
}
