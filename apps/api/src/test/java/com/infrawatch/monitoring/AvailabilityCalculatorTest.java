package com.infrawatch.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.infrawatch.resource.ResourceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID R = UUID.randomUUID();
    private static final UUID O = UUID.randomUUID();

    private static ResourceStatusHistory t(ResourceStatus from, ResourceStatus to, long minute) {
        return new ResourceStatusHistory(R, O, from, to, null, null, T0.plusSeconds(minute * 60));
    }

    @Test
    @DisplayName("sem estado conhecido, a disponibilidade é indefinida (nunca 100%)")
    void noKnownStateMeansUndefined() {
        var r = AvailabilityCalculator.compute(T0, T0.plusSeconds(3600), ResourceStatus.PENDING, List.of());
        assertThat(r.percent()).isNull();
        assertThat(r.observedSeconds()).isZero();
    }

    @Test
    @DisplayName("tempo aguardando é excluído do observado")
    void pendingTimeIsExcluded() {
        // 10 min aguardando, depois 50 min disponível
        var r = AvailabilityCalculator.compute(T0, T0.plusSeconds(3600), ResourceStatus.PENDING,
                List.of(t(ResourceStatus.PENDING, ResourceStatus.UP, 10)));
        assertThat(r.observedSeconds()).isEqualTo(50 * 60);
        assertThat(r.percent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("indisponibilidade é descontada proporcionalmente")
    void downtimeReducesAvailability() {
        // 0-30 UP, 30-45 DOWN, 45-60 UP  => 45/60 = 75%
        var r = AvailabilityCalculator.compute(T0, T0.plusSeconds(3600), ResourceStatus.UP,
                List.of(t(ResourceStatus.UP, ResourceStatus.DOWN, 30), t(ResourceStatus.DOWN, ResourceStatus.UP, 45)));
        assertThat(r.observedSeconds()).isEqualTo(3600);
        assertThat(r.availableSeconds()).isEqualTo(45 * 60);
        assertThat(r.percent()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("degradado conta como disponível; sem dados é excluído")
    void degradedCountsAsAvailableUnknownExcluded() {
        // 0-20 DEGRADED, 20-40 UNKNOWN, 40-60 DOWN => observado 40 min, disponível 20 => 50%
        var r = AvailabilityCalculator.compute(T0, T0.plusSeconds(3600), ResourceStatus.DEGRADED,
                List.of(t(ResourceStatus.DEGRADED, ResourceStatus.UNKNOWN, 20), t(ResourceStatus.UNKNOWN, ResourceStatus.DOWN, 40)));
        assertThat(r.observedSeconds()).isEqualTo(40 * 60);
        assertThat(r.percent()).isEqualTo(50.0);
    }
}
