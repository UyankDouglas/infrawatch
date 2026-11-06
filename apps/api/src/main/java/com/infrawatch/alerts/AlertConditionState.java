package com.infrawatch.alerts;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Contagem de ocorrências consecutivas e início da violação para condições numéricas (regra × recurso). */
@Entity
@Table(name = "alert_condition_states")
public class AlertConditionState {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "rule_id", nullable = false) private UUID ruleId;
        @Column(name = "resource_id", nullable = false) private UUID resourceId;

        protected Key() {}
        public Key(UUID ruleId, UUID resourceId) { this.ruleId = ruleId; this.resourceId = resourceId; }
        public UUID getRuleId() { return ruleId; }
        public UUID getResourceId() { return resourceId; }
        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(ruleId, k.ruleId) && Objects.equals(resourceId, k.resourceId);
        }
        @Override public int hashCode() { return Objects.hash(ruleId, resourceId); }
    }

    @EmbeddedId
    private Key key;

    @Column(name = "breaching_since")
    private Instant breachingSince;

    @Column(nullable = false)
    private int occurrences;

    @Column(name = "last_value", precision = 12, scale = 2)
    private BigDecimal lastValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AlertConditionState() {}

    public AlertConditionState(UUID ruleId, UUID resourceId) {
        this.key = new Key(ruleId, resourceId);
        this.updatedAt = Instant.now();
    }

    public void breach(BigDecimal value, Instant at) {
        if (breachingSince == null) breachingSince = at;
        occurrences++;
        lastValue = value;
        updatedAt = at;
    }

    public void clear(BigDecimal value, Instant at) {
        breachingSince = null;
        occurrences = 0;
        lastValue = value;
        updatedAt = at;
    }

    public Key getKey() { return key; }
    public Instant getBreachingSince() { return breachingSince; }
    public int getOccurrences() { return occurrences; }
    public BigDecimal getLastValue() { return lastValue; }
}
