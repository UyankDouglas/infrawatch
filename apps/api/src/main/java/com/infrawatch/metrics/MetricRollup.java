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

/** Agregação de amostras por intervalo (300 s ou 3600 s). Mantida por mais tempo que as amostras brutas. */
@Entity
@Table(name = "metric_rollups")
public class MetricRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "bucket_seconds", nullable = false)
    private int bucketSeconds;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(nullable = false)
    private int samples;

    @Column(name = "cpu_avg", precision = 5, scale = 2) private BigDecimal cpuAvg;
    @Column(name = "cpu_max", precision = 5, scale = 2) private BigDecimal cpuMax;
    @Column(name = "mem_percent_avg", precision = 5, scale = 2) private BigDecimal memPercentAvg;
    @Column(name = "mem_percent_max", precision = 5, scale = 2) private BigDecimal memPercentMax;
    @Column(name = "mem_used_avg") private Long memUsedAvg;
    @Column(name = "disk_percent_avg", precision = 5, scale = 2) private BigDecimal diskPercentAvg;
    @Column(name = "disk_percent_max", precision = 5, scale = 2) private BigDecimal diskPercentMax;
    @Column(name = "disk_used_avg") private Long diskUsedAvg;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MetricRollup() {}

    public Long getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public int getBucketSeconds() { return bucketSeconds; }
    public Instant getBucketStart() { return bucketStart; }
    public int getSamples() { return samples; }
    public BigDecimal getCpuAvg() { return cpuAvg; }
    public BigDecimal getMemPercentAvg() { return memPercentAvg; }
    public BigDecimal getDiskPercentAvg() { return diskPercentAvg; }
}
