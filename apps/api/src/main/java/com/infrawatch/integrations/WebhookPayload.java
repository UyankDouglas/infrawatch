package com.infrawatch.integrations;

import com.infrawatch.alerts.AlertEnums.Severity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Corpo JSON enviado aos webhooks e assinatura HMAC-SHA256 ({@code X-InfraWatch-Signature: sha256=<hex>}). */
public final class WebhookPayload {
    private WebhookPayload() {}

    public static String json(UUID organizationId, String eventType, Severity severity, String title, String message,
                              String link, UUID resourceId, Instant at) {
        return "{"
                + "\"event\":" + q(eventType)
                + ",\"severity\":" + q(severity.name())
                + ",\"severityLabel\":" + q(severity.getLabel())
                + ",\"title\":" + q(title)
                + ",\"message\":" + q(message)
                + ",\"link\":" + q(link)
                + ",\"resourceId\":" + q(resourceId == null ? null : resourceId.toString())
                + ",\"organizationId\":" + q(organizationId.toString())
                + ",\"occurredAt\":" + q(at.toString())
                + ",\"source\":\"infrawatch\""
                + "}";
    }

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar webhook", e);
        }
    }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
