package com.infrawatch.monitoring;

import com.infrawatch.common.ApiException;
import java.time.Duration;
import java.util.Map;

/** Períodos aceitos pelos endpoints de dashboard/métricas e o tamanho de bucket correspondente. */
public final class Periods {
    private static final Map<String, Duration> PERIODS = Map.of(
            "1h", Duration.ofHours(1),
            "6h", Duration.ofHours(6),
            "24h", Duration.ofHours(24),
            "7d", Duration.ofDays(7),
            "30d", Duration.ofDays(30));

    private Periods() {}

    public static Duration parse(String period) {
        Duration d = PERIODS.get(period == null ? "24h" : period.trim().toLowerCase());
        if (d == null) {
            throw ApiException.badRequest("invalid_period", "Período inválido. Use: 1h, 6h, 24h, 7d ou 30d.");
        }
        return d;
    }

    public static int bucketSeconds(Duration window) {
        long hours = window.toHours();
        if (hours <= 1) return 60;          // 1 min
        if (hours <= 6) return 300;         // 5 min
        if (hours <= 24) return 900;        // 15 min
        if (hours <= 24 * 7) return 3600;   // 1 h
        return 6 * 3600;                    // 6 h
    }
}
