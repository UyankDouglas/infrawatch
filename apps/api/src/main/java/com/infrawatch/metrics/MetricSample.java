package com.infrawatch.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Amostra bruta enviada por um agente. Campos nulos significam "métrica não suportada/indisponível". */
@Entity
@Table(name = "metric_samples")
public class MetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "cpu_percent", precision = 5, scale = 2)
    private BigDecimal cpuPercent;

    @Column(name = "load_avg_1m", precision = 8, scale = 2)
    private BigDecimal loadAvg1m;

    @Column(name = "mem_total_bytes")
    private Long memTotalBytes;

    @Column(name = "mem_used_bytes")
    private Long memUsedBytes;

    @Column(name = "mem_percent", precision = 5, scale = 2)
    private BigDecimal memPercent;

    @Column(name = "disk_path", length = 255)
    private String diskPath;

    @Column(name = "disk_total_bytes")
    private Long diskTotalBytes;

    @Column(name = "disk_used_bytes")
    private Long diskUsedBytes;

    @Column(name = "disk_percent", precision = 5, scale = 2)
    private BigDecimal diskPercent;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    protected MetricSample() {}

    public MetricSample(UUID resourceId, UUID organizationId, UUID agentId, Instant sampledAt, Instant receivedAt) {
        this.resourceId = resourceId;
        this.organizationId = organizationId;
        this.agentId = agentId;
        this.sampledAt = sampledAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getAgentId() { return agentId; }
    public Instant getSampledAt() { return sampledAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public BigDecimal getCpuPercent() { return cpuPercent; }
    public BigDecimal getLoadAvg1m() { return loadAvg1m; }
    public Long getMemTotalBytes() { return memTotalBytes; }
    public Long getMemUsedBytes() { return memUsedBytes; }
    public BigDecimal getMemPercent() { return memPercent; }
    public String getDiskPath() { return diskPath; }
    public Long getDiskTotalBytes() { return diskTotalBytes; }
    public Long getDiskUsedBytes() { return diskUsedBytes; }
    public BigDecimal getDiskPercent() { return diskPercent; }
    public Long getUptimeSeconds() { return uptimeSeconds; }

    public void setCpu(BigDecimal cpuPercent, BigDecimal loadAvg1m) {
        this.cpuPercent = cpuPercent;
        this.loadAvg1m = loadAvg1m;
    }

    public void setMemory(Long total, Long used, BigDecimal percent) {
        this.memTotalBytes = total;
        this.memUsedBytes = used;
        this.memPercent = percent;
    }

    public void setDisk(String path, Long total, Long used, BigDecimal percent) {
        this.diskPath = path;
        this.diskTotalBytes = total;
        this.diskUsedBytes = used;
        this.diskPercent = percent;
    }

    public void setUptimeSeconds(Long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
}
