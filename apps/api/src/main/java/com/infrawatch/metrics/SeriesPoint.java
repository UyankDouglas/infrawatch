package com.infrawatch.metrics;

import java.math.BigDecimal;
import java.time.Instant;

/** Projeção de série temporal (amostras brutas agrupadas ou rollups) usada pela API de métricas. */
public interface SeriesPoint {
    Instant getBucket();
    Integer getSamples();
    BigDecimal getCpuAvg();
    BigDecimal getCpuMax();
    BigDecimal getMemPercentAvg();
    BigDecimal getMemPercentMax();
    Long getMemUsedAvg();
    BigDecimal getDiskPercentAvg();
    BigDecimal getDiskPercentMax();
    Long getDiskUsedAvg();
}
