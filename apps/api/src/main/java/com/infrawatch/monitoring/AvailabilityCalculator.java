package com.infrawatch.monitoring;

import com.infrawatch.resource.ResourceStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Disponibilidade baseada em tempo, calculada a partir das transições de estado.
 *
 * Disponibilidade (%) = tempo disponível / tempo observado × 100
 *
 *  - Tempo observado: soma dos intervalos em que o estado era conhecido (DISPONÍVEL, DEGRADADO, INDISPONÍVEL).
 *  - AGUARDANDO, SEM DADOS e EM MANUTENÇÃO são excluídos do tempo observado (não contam nem a favor nem contra).
 *  - DEGRADADO conta como disponível (o serviço responde, ainda que lento).
 *  - Sem nenhum intervalo conhecido, o resultado é indefinido (null), nunca 100%.
 */
public final class AvailabilityCalculator {

    public record Result(Double percent, long observedSeconds, long availableSeconds) {
        public static final Result EMPTY = new Result(null, 0, 0);
    }

    private AvailabilityCalculator() {}

    private static boolean counts(ResourceStatus status, MaintenanceMode mode) {
        if (status == ResourceStatus.MAINTENANCE) return mode == MaintenanceMode.COUNTED_AS_DOWNTIME;
        return status.isKnown();
    }

    /**
     * @param statusAtStart estado vigente no início da janela
     * @param transitions   transições dentro da janela, em ordem cronológica
     */
    /** Como tratar períodos em manutenção: EXCLUDED (padrão: fora do tempo observado) ou COUNTED_AS_DOWNTIME. */
    public enum MaintenanceMode { EXCLUDED, COUNTED_AS_DOWNTIME }

    public static Result compute(Instant from, Instant to, ResourceStatus statusAtStart,
                                 List<ResourceStatusHistory> transitions) {
        return compute(from, to, statusAtStart, transitions, MaintenanceMode.EXCLUDED);
    }

    public static Result compute(Instant from, Instant to, ResourceStatus statusAtStart,
                                 List<ResourceStatusHistory> transitions, MaintenanceMode mode) {
        long observed = 0, available = 0;
        Instant cursor = from;
        ResourceStatus current = statusAtStart == null ? ResourceStatus.PENDING : statusAtStart;
        for (ResourceStatusHistory t : transitions) {
            Instant at = t.getChangedAt().isBefore(from) ? from : t.getChangedAt();
            if (at.isAfter(to)) break;
            long span = Math.max(0, Duration.between(cursor, at).toSeconds());
            if (counts(current, mode)) {
                observed += span;
                if (current.isAvailable()) available += span;
            }
            cursor = at;
            current = t.getToStatus();
        }
        long tail = Math.max(0, Duration.between(cursor, to).toSeconds());
        if (counts(current, mode)) {
            observed += tail;
            if (current.isAvailable()) available += tail;
        }
        if (observed == 0) return Result.EMPTY;
        return new Result(Math.round(available * 10000.0 / observed) / 100.0, observed, available);
    }
}
