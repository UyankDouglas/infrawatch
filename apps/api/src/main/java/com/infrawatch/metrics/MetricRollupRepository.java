package com.infrawatch.metrics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetricRollupRepository extends JpaRepository<MetricRollup, Long> {

    @Query(value = """
            SELECT bucket_start AS bucket, samples, cpu_avg AS cpuAvg, cpu_max AS cpuMax,
                   mem_percent_avg AS memPercentAvg, mem_percent_max AS memPercentMax, mem_used_avg AS memUsedAvg,
                   disk_percent_avg AS diskPercentAvg, disk_percent_max AS diskPercentMax, disk_used_avg AS diskUsedAvg
              FROM metric_rollups
             WHERE resource_id = :resourceId AND bucket_seconds = :bucketSeconds
               AND bucket_start >= :from AND bucket_start < :to
             ORDER BY bucket_start
            """, nativeQuery = true)
    List<SeriesPoint> series(@Param("resourceId") UUID resourceId, @Param("bucketSeconds") int bucketSeconds,
                             @Param("from") Instant from, @Param("to") Instant to);

    long countByResourceIdAndBucketSeconds(UUID resourceId, int bucketSeconds);

    @Modifying
    @Query(value = "DELETE FROM metric_rollups WHERE bucket_start < :threshold", nativeQuery = true)
    int deleteOlderThan(@Param("threshold") Instant threshold);
}
