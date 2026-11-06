package com.infrawatch.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro imutável de uma verificação. Nunca guarda cookies, tokens ou corpo de resposta. */
@Entity
@Table(name = "health_check_executions")
public class HealthCheckExecution {

    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "check_type", nullable = false, length = 30)
    private String checkType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "resolved_address", length = 80)
    private String resolvedAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HealthCheckExecution() {}

    public HealthCheckExecution(UUID resourceId, UUID organizationId, UUID runId, String checkType, CheckOutcome o) {
        this.id = UUID.randomUUID();
        this.resourceId = resourceId;
        this.organizationId = organizationId;
        this.runId = runId;
        this.checkType = checkType;
        this.startedAt = o.startedAt();
        this.finishedAt = o.finishedAt();
        this.durationMs = (int) Math.max(0, o.finishedAt().toEpochMilli() - o.startedAt().toEpochMilli());
        this.success = o.success();
        this.httpStatus = o.httpStatus();
        this.responseTimeMs = o.responseTimeMs();
        this.errorCode = o.errorCode();
        this.errorMessage = o.errorMessage() == null ? null
                : o.errorMessage().substring(0, Math.min(500, o.errorMessage().length()));
        this.resolvedAddress = o.resolvedAddress();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getRunId() { return runId; }
    public String getCheckType() { return checkType; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getDurationMs() { return durationMs; }
    public boolean isSuccess() { return success; }
    public Integer getHttpStatus() { return httpStatus; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getResolvedAddress() { return resolvedAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
