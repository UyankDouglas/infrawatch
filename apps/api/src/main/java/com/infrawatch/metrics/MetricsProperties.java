package com.infrawatch.metrics;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrawatch.metrics")
public record MetricsProperties(
        Duration rawRetention,
        Duration rollupRetention,
        Duration checkRetention,
        Duration minIngestSpacing,
        Duration maxClockSkew,
        int staleAfterMultiplier) {

    public MetricsProperties {
        if (rawRetention == null) rawRetention = Duration.ofDays(7);
        if (rollupRetention == null) rollupRetention = Duration.ofDays(90);
        if (checkRetention == null) checkRetention = Duration.ofDays(30);
        if (minIngestSpacing == null) minIngestSpacing = Duration.ofSeconds(5);
        if (maxClockSkew == null) maxClockSkew = Duration.ofMinutes(5);
        if (staleAfterMultiplier <= 0) staleAfterMultiplier = 3;
    }
}
