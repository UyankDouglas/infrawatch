package com.infrawatch.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetricSampleRepository extends JpaRepository<MetricSample, Long> {

    Optional<MetricSample> findFirstByResourceIdOrderBySampledAtDesc(UUID resourceId);

    long countByResourceId(UUID resourceId);

    @Query(value = """
            SELECT date_bin(make_interval(secs => :bucketSeconds), sampled_at, TIMESTAMPTZ '2000-01-01') AS bucket,
                   count(*)                     AS samples,
                   avg(cpu_percent)             AS cpuAvg,
                   max(cpu_percent)             AS cpuMax,
                   avg(mem_percent)             AS memPercentAvg,
                   max(mem_percent)             AS memPercentMax,
                   avg(mem_used_bytes)::bigint  AS memUsedAvg,
                   avg(disk_percent)            AS diskPercentAvg,
                   max(disk_percent)            AS diskPercentMax,
                   avg(disk_used_bytes)::bigint AS diskUsedAvg
              FROM metric_samples
             WHERE resource_id = :resourceId AND sampled_at >= :from AND sampled_at < :to
             GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<SeriesPoint> seriesFromSamples(@Param("resourceId") UUID resourceId, @Param("from") Instant from,
                                        @Param("to") Instant to, @Param("bucketSeconds") int bucketSeconds);

    @Modifying
    @Query(value = "DELETE FROM metric_samples WHERE sampled_at < :threshold", nativeQuery = true)
    int deleteOlderThan(@Param("threshold") Instant threshold);

    /** Agrega amostras em buckets e grava/atualiza os rollups (idempotente). */
    @Modifying
    @Query(value = """
            INSERT INTO metric_rollups (resource_id, organization_id, bucket_seconds, bucket_start, samples,
                    cpu_avg, cpu_max, mem_percent_avg, mem_percent_max, mem_used_avg,
                    disk_percent_avg, disk_percent_max, disk_used_avg, updated_at)
            SELECT resource_id, organization_id, :bucketSeconds,
                   date_bin(make_interval(secs => :bucketSeconds), sampled_at, TIMESTAMPTZ '2000-01-01') AS bucket,
                   count(*), avg(cpu_percent), max(cpu_percent), avg(mem_percent), max(mem_percent),
                   avg(mem_used_bytes)::bigint, avg(disk_percent), max(disk_percent), avg(disk_used_bytes)::bigint, now()
              FROM metric_samples
             WHERE sampled_at >= :from AND sampled_at < :to
             GROUP BY resource_id, organization_id, bucket
            ON CONFLICT (resource_id, bucket_seconds, bucket_start) DO UPDATE SET
                    samples = EXCLUDED.samples, cpu_avg = EXCLUDED.cpu_avg, cpu_max = EXCLUDED.cpu_max,
                    mem_percent_avg = EXCLUDED.mem_percent_avg, mem_percent_max = EXCLUDED.mem_percent_max,
                    mem_used_avg = EXCLUDED.mem_used_avg, disk_percent_avg = EXCLUDED.disk_percent_avg,
                    disk_percent_max = EXCLUDED.disk_percent_max, disk_used_avg = EXCLUDED.disk_used_avg,
                    updated_at = now()
            """, nativeQuery = true)
    int rollup(@Param("from") Instant from, @Param("to") Instant to, @Param("bucketSeconds") int bucketSeconds);
}
