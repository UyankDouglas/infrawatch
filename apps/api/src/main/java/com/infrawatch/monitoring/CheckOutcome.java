package com.infrawatch.monitoring;

import java.time.Instant;

/** Resultado bruto de uma verificação, antes da avaliação de estado. */
public record CheckOutcome(
        boolean success,
        Integer httpStatus,
        Integer responseTimeMs,
        String errorCode,
        String errorMessage,
        String resolvedAddress,
        Instant startedAt,
        Instant finishedAt) {

    public static CheckOutcome failure(String code, String message, String resolvedAddress, Instant started,
                                       Integer httpStatus, Integer responseTimeMs) {
        return new CheckOutcome(false, httpStatus, responseTimeMs, code, message, resolvedAddress, started,
                Instant.now());
    }
}
