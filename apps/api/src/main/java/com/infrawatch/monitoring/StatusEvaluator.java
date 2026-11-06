package com.infrawatch.monitoring;

import com.infrawatch.resource.ResourceStatus;

/**
 * Máquina de estados com histerese. Função pura: recebe o estado atual, os contadores e o resultado da
 * verificação e devolve o novo estado. Regras (configuráveis por recurso):
 *
 *  - Falha confirma INDISPONÍVEL somente após {@code failureThreshold} falhas consecutivas.
 *  - A partir de INDISPONÍVEL, a recuperação exige {@code recoveryThreshold} sucessos consecutivos.
 *  - Sucesso com latência acima de {@code degradedLatencyMs} conta como "lento"; {@code failureThreshold}
 *    respostas lentas consecutivas confirmam DEGRADADO; {@code recoveryThreshold} respostas rápidas
 *    consecutivas voltam a DISPONÍVEL.
 *  - Estados sem coleta (AGUARDANDO / SEM DADOS) transitam no primeiro sucesso para DISPONÍVEL (ou DEGRADADO,
 *    se lento), pois não há falha prévia a confirmar.
 *  - Falhas abaixo do limiar não alteram o estado atual (evita flapping).
 *
 * Contadores: {@code failures} = falhas consecutivas; {@code successes} = sucessos consecutivos
 * (em DEGRADADO, sucessos rápidos consecutivos); {@code slow} = sucessos lentos consecutivos.
 */
public final class StatusEvaluator {

    public record State(ResourceStatus status, int failures, int successes, int slow) {}

    public record Thresholds(int failureThreshold, int recoveryThreshold, Integer degradedLatencyMs) {}

    public record Decision(ResourceStatus status, int failures, int successes, int slow, String reason) {
        public boolean changed(ResourceStatus previous) { return status != previous; }
    }

    private StatusEvaluator() {}

    public static Decision evaluate(State current, Thresholds t, boolean success, Integer responseTimeMs,
                                    String errorCode) {
        ResourceStatus status = current.status();

        if (!success) {
            int failures = current.failures() + 1;
            if (status != ResourceStatus.DOWN && status != ResourceStatus.MAINTENANCE
                    && failures >= t.failureThreshold()) {
                return new Decision(ResourceStatus.DOWN, failures, 0, 0,
                        failures + " falha(s) consecutiva(s) — última: " + (errorCode == null ? "erro" : errorCode));
            }
            return new Decision(status, failures, 0, 0, null);
        }

        boolean slow = t.degradedLatencyMs() != null && responseTimeMs != null
                && responseTimeMs > t.degradedLatencyMs();

        return switch (status) {
            case MAINTENANCE -> new Decision(status, 0, current.successes() + 1, slow ? current.slow() + 1 : 0, null);

            case PENDING, UNKNOWN -> new Decision(slow ? ResourceStatus.DEGRADED : ResourceStatus.UP, 0, 1,
                    slow ? 1 : 0, slow ? "Primeira coleta com latência acima do limite" : "Primeira coleta bem-sucedida");

            case DOWN -> {
                int successes = current.successes() + 1;
                int slowCount = slow ? current.slow() + 1 : 0;
                if (successes >= t.recoveryThreshold()) {
                    ResourceStatus next = slow ? ResourceStatus.DEGRADED : ResourceStatus.UP;
                    yield new Decision(next, 0, slow ? 0 : successes, slowCount,
                            "Recuperado após " + successes + " sucesso(s) consecutivo(s)");
                }
                yield new Decision(status, 0, successes, slowCount, null);
            }

            case DEGRADED -> {
                if (slow) {
                    yield new Decision(status, 0, 0, current.slow() + 1, null);
                }
                int fastStreak = current.successes() + 1;
                if (fastStreak >= t.recoveryThreshold()) {
                    yield new Decision(ResourceStatus.UP, 0, fastStreak, 0,
                            "Latência normalizada após " + fastStreak + " resposta(s) rápida(s)");
                }
                yield new Decision(status, 0, fastStreak, 0, null);
            }

            case UP -> {
                int successes = current.successes() + 1;
                int slowCount = slow ? current.slow() + 1 : 0;
                if (slow && slowCount >= t.failureThreshold()) {
                    yield new Decision(ResourceStatus.DEGRADED, 0, 0, slowCount,
                            slowCount + " resposta(s) consecutiva(s) acima de " + t.degradedLatencyMs() + " ms");
                }
                yield new Decision(status, 0, successes, slowCount, null);
            }
        };
    }
}
