package com.infrawatch.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Parâmetros da verificação HTTP de um recurso (intervalo, timeout, códigos esperados e histerese). */
@Entity
@Table(name = "health_check_configs")
public class HealthCheckConfig {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false, unique = true)
    private MonitoredResource resource;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds = 60;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 5000;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod = "GET";

    /** Ex.: "200-299,301". Nunca aceita qualquer código automaticamente. */
    @Column(name = "expected_status_codes", nullable = false, length = 120)
    private String expectedStatusCodes = "200-299";

    @Column(name = "degraded_latency_ms")
    private Integer degradedLatencyMs;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold = 3;

    @Column(name = "recovery_threshold", nullable = false)
    private int recoveryThreshold = 2;

    @Column(name = "follow_redirects", nullable = false)
    private boolean followRedirects = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HealthCheckConfig() {}

    public HealthCheckConfig(MonitoredResource resource) {
        this.id = UUID.randomUUID();
        this.resource = resource;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public MonitoredResource getResource() { return resource; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getTimeoutMs() { return timeoutMs; }
    public String getHttpMethod() { return httpMethod; }
    public String getExpectedStatusCodes() { return expectedStatusCodes; }
    public Integer getDegradedLatencyMs() { return degradedLatencyMs; }
    public int getFailureThreshold() { return failureThreshold; }
    public int getRecoveryThreshold() { return recoveryThreshold; }
    public boolean isFollowRedirects() { return followRedirects; }

    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public void setExpectedStatusCodes(String expectedStatusCodes) { this.expectedStatusCodes = expectedStatusCodes; }
    public void setDegradedLatencyMs(Integer degradedLatencyMs) { this.degradedLatencyMs = degradedLatencyMs; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
    public void setRecoveryThreshold(int recoveryThreshold) { this.recoveryThreshold = recoveryThreshold; }
    public void setFollowRedirects(boolean followRedirects) { this.followRedirects = followRedirects; }
}
