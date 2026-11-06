package com.infrawatch.integrations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrawatch.integrations")
public record IntegrationsProperties(
        boolean dispatchEnabled,
        String baseUrl,
        String fromAddress,
        int maxAttempts,
        Duration expireAfter,
        Duration webhookTimeout) {

    public IntegrationsProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:5174";
        if (fromAddress == null || fromAddress.isBlank()) fromAddress = "infrawatch@localhost";
        if (maxAttempts <= 0) maxAttempts = 5;
        if (expireAfter == null) expireAfter = Duration.ofHours(6);
        if (webhookTimeout == null) webhookTimeout = Duration.ofSeconds(10);
    }
}
