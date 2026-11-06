package com.infrawatch.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.infrawatch.monitoring.StatusEvaluator.Decision;
import com.infrawatch.monitoring.StatusEvaluator.State;
import com.infrawatch.monitoring.StatusEvaluator.Thresholds;
import com.infrawatch.resource.ResourceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusEvaluatorTest {

    private static final Thresholds T = new Thresholds(3, 2, 800);

    private static Decision run(State start, boolean[] successes, Integer latency) {
        State state = start;
        Decision last = null;
        for (boolean ok : successes) {
            last = StatusEvaluator.evaluate(state, T, ok, ok ? latency : null, ok ? null : "unexpected_status");
            state = new State(last.status(), last.failures(), last.successes(), last.slow());
        }
        return last;
    }

    @Test
    @DisplayName("primeira coleta bem-sucedida leva de AGUARDANDO para DISPONÍVEL")
    void firstSuccessBecomesUp() {
        Decision d = StatusEvaluator.evaluate(new State(ResourceStatus.PENDING, 0, 0, 0), T, true, 50, null);
        assertThat(d.status()).isEqualTo(ResourceStatus.UP);
        assertThat(d.reason()).contains("Primeira coleta");
    }

    @Test
    @DisplayName("falhas abaixo do limiar não alteram o estado (sem flapping)")
    void failuresBelowThresholdKeepStatus() {
        Decision d = run(new State(ResourceStatus.UP, 0, 5, 0), new boolean[]{false, false}, null);
        assertThat(d.status()).isEqualTo(ResourceStatus.UP);
        assertThat(d.failures()).isEqualTo(2);
    }

    @Test
    @DisplayName("três falhas consecutivas confirmam INDISPONÍVEL")
    void threeFailuresBecomeDown() {
        Decision d = run(new State(ResourceStatus.UP, 0, 5, 0), new boolean[]{false, false, false}, null);
        assertThat(d.status()).isEqualTo(ResourceStatus.DOWN);
        assertThat(d.reason()).contains("3 falha");
    }

    @Test
    @DisplayName("recuperação exige dois sucessos consecutivos")
    void recoveryRequiresTwoSuccesses() {
        Decision one = run(new State(ResourceStatus.DOWN, 3, 0, 0), new boolean[]{true}, 40);
        assertThat(one.status()).isEqualTo(ResourceStatus.DOWN);
        Decision two = run(new State(ResourceStatus.DOWN, 3, 0, 0), new boolean[]{true, true}, 40);
        assertThat(two.status()).isEqualTo(ResourceStatus.UP);
        assertThat(two.reason()).contains("Recuperado");
    }

    @Test
    @DisplayName("falha intercalada reinicia a contagem de recuperação")
    void interleavedFailureResetsRecovery() {
        Decision d = run(new State(ResourceStatus.DOWN, 3, 0, 0), new boolean[]{true, false, true}, 40);
        assertThat(d.status()).isEqualTo(ResourceStatus.DOWN);
        assertThat(d.successes()).isEqualTo(1);
    }

    @Test
    @DisplayName("latência acima do limite por três coletas confirma DEGRADADO")
    void slowResponsesBecomeDegraded() {
        Decision d = run(new State(ResourceStatus.UP, 0, 1, 0), new boolean[]{true, true, true}, 1500);
        assertThat(d.status()).isEqualTo(ResourceStatus.DEGRADED);
        Decision two = run(new State(ResourceStatus.UP, 0, 1, 0), new boolean[]{true, true}, 1500);
        assertThat(two.status()).isEqualTo(ResourceStatus.UP);
    }

    @Test
    @DisplayName("DEGRADADO volta a DISPONÍVEL após duas respostas rápidas consecutivas")
    void degradedRecoversAfterFastResponses() {
        Decision one = run(new State(ResourceStatus.DEGRADED, 0, 0, 3), new boolean[]{true}, 100);
        assertThat(one.status()).isEqualTo(ResourceStatus.DEGRADED);
        Decision two = run(new State(ResourceStatus.DEGRADED, 0, 0, 3), new boolean[]{true, true}, 100);
        assertThat(two.status()).isEqualTo(ResourceStatus.UP);
    }

    @Test
    @DisplayName("sem limite de degradação configurado, latência alta não degrada")
    void noDegradedThresholdMeansNoDegradation() {
        Thresholds noLatency = new Thresholds(3, 2, null);
        Decision d = StatusEvaluator.evaluate(new State(ResourceStatus.UP, 0, 1, 0), noLatency, true, 9000, null);
        assertThat(d.status()).isEqualTo(ResourceStatus.UP);
    }

    @Test
    @DisplayName("recurso SEM DADOS que falha três vezes vai para INDISPONÍVEL")
    void unknownFailingBecomesDown() {
        Decision d = run(new State(ResourceStatus.UNKNOWN, 0, 0, 0), new boolean[]{false, false, false}, null);
        assertThat(d.status()).isEqualTo(ResourceStatus.DOWN);
    }
}
