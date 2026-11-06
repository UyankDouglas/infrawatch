package com.infrawatch.monitoring;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrawatch.monitoring")
public record MonitoringProperties(
        boolean enabled,
        Duration dispatcherInterval,
        int maxConcurrentChecks,
        int batchSize,
        int minIntervalSeconds,
        int maxTimeoutMs,
        int maxResponseBytes,
        int maxRedirects,
        int staleAfterMultiplier,
        TargetPolicy targetPolicy) {

    public MonitoringProperties {
        if (dispatcherInterval == null) dispatcherInterval = Duration.ofSeconds(5);
        if (maxConcurrentChecks <= 0) maxConcurrentChecks = 8;
        if (batchSize <= 0) batchSize = 50;
        if (minIntervalSeconds <= 0) minIntervalSeconds = 30;
        if (maxTimeoutMs <= 0) maxTimeoutMs = 30_000;
        if (maxResponseBytes <= 0) maxResponseBytes = 65_536;
        if (maxRedirects < 0) maxRedirects = 3;
        if (staleAfterMultiplier <= 0) staleAfterMultiplier = 3;
        if (targetPolicy == null) targetPolicy = new TargetPolicy(TargetPolicyMode.ALLOWLIST_ONLY, List.of(), List.of());
    }

    public enum TargetPolicyMode {
        /** Somente hosts explicitamente autorizados (padrão). */
        ALLOWLIST_ONLY,
        /** Hosts autorizados + qualquer host público que não resolva para redes internas. */
        PUBLIC_AND_ALLOWLIST
    }

    public record TargetPolicy(TargetPolicyMode mode, List<String> allowedHosts, List<Integer> allowedPorts) {
        public TargetPolicy {
            if (mode == null) mode = TargetPolicyMode.ALLOWLIST_ONLY;
            if (allowedHosts == null) allowedHosts = List.of();
            if (allowedPorts == null) allowedPorts = List.of();
        }
    }
}
