package com.infrawatch.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrawatch.seed")
public record SeedProperties(boolean enabled, String demoBaseUrl) {
    public SeedProperties {
        if (demoBaseUrl == null || demoBaseUrl.isBlank()) demoBaseUrl = "http://localhost";
    }
}
