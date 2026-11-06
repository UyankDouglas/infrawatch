package com.infrawatch.resource;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "monitored_resources")
public class MonitoredResource {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private ResourceGroup group;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResourceType type;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @Column(length = 120)
    private String owner;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 20)
    private ResourceStatus currentStatus = ResourceStatus.PENDING;

    @Column(name = "status_since", nullable = false)
    private Instant statusSince;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "last_success")
    private Boolean lastSuccess;

    @Column(name = "last_response_time_ms")
    private Integer lastResponseTimeMs;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    @Column(name = "consecutive_slow", nullable = false)
    private int consecutiveSlow;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monitored_resource_tags", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "tag", nullable = false, length = 40)
    private Set<String> tags = new LinkedHashSet<>();

    @OneToOne(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private HealthCheckConfig checkConfig;

    protected MonitoredResource() {}

    public MonitoredResource(UUID organizationId, String name, ResourceType type, String targetUrl,
                             Environment environment, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.type = type;
        this.targetUrl = targetUrl;
        this.environment = environment;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.statusSince = now;
        this.nextCheckAt = now;
        this.checkConfig = new HealthCheckConfig(this);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    // --- Transições de estado (usadas somente pelo motor de verificações) ---

    public void applyStatus(ResourceStatus newStatus, Instant at) {
        if (newStatus != this.currentStatus) {
            this.currentStatus = newStatus;
            this.statusSince = at;
        }
    }

    public void recordCheck(Instant checkedAt, boolean success, Integer responseTimeMs, Integer httpStatus,
                            String errorCode) {
        this.lastCheckAt = checkedAt;
        this.lastSuccess = success;
        this.lastResponseTimeMs = responseTimeMs;
        this.lastHttpStatus = httpStatus;
        this.lastErrorCode = errorCode;
        this.lastEvaluatedAt = checkedAt;
    }

    public void setCounters(int failures, int successes, int slow) {
        this.consecutiveFailures = failures;
        this.consecutiveSuccesses = successes;
        this.consecutiveSlow = slow;
    }

    public void resetCounters() { setCounters(0, 0, 0); }

    // --- Getters/Setters ---

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public ResourceGroup getGroup() { return group; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ResourceType getType() { return type; }
    public String getTargetUrl() { return targetUrl; }
    public Environment getEnvironment() { return environment; }
    public String getOwner() { return owner; }
    public boolean isEnabled() { return enabled; }
    public ResourceStatus getCurrentStatus() { return currentStatus; }
    public Instant getStatusSince() { return statusSince; }
    public Instant getLastCheckAt() { return lastCheckAt; }
    public Boolean getLastSuccess() { return lastSuccess; }
    public Integer getLastResponseTimeMs() { return lastResponseTimeMs; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public String getLastErrorCode() { return lastErrorCode; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public int getConsecutiveSlow() { return consecutiveSlow; }
    public Instant getNextCheckAt() { return nextCheckAt; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public long getVersion() { return version; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<String> getTags() { return tags; }
    public HealthCheckConfig getCheckConfig() { return checkConfig; }

    public void setGroup(ResourceGroup group) { this.group = group; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setType(ResourceType type) { this.type = type; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public void setEnvironment(Environment environment) { this.environment = environment; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setNextCheckAt(Instant nextCheckAt) { this.nextCheckAt = nextCheckAt; }
    public void setTags(Set<String> tags) { this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags); }
}
