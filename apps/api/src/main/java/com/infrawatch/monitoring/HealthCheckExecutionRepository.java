package com.infrawatch.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthCheckExecutionRepository extends JpaRepository<HealthCheckExecution, UUID> {

    Page<HealthCheckExecution> findByResourceIdAndOrganizationIdOrderByStartedAtDesc(UUID resourceId,
                                                                                     UUID organizationId,
                                                                                     Pageable pageable);

    List<HealthCheckExecution> findByResourceIdAndOrganizationIdAndStartedAtBetweenOrderByStartedAtAsc(
            UUID resourceId, UUID organizationId, Instant from, Instant to);

    long countByResourceId(UUID resourceId);

    interface BucketStats {
        Instant getBucket();
        long getTotal();
        long getSuccesses();
        Double getAvgResponseMs();
        Integer getMaxResponseMs();
    }

    interface ResourceBucketStats extends BucketStats {
        UUID getResourceId();
    }

    interface ResourceLatencyStats {
        UUID getResourceId();
        long getTotal();
        long getFailures();
        Double getAvgResponseMs();
        Integer getMaxResponseMs();
        Double getP95ResponseMs();
    }

    @Query(value = """
            SELECT resource_id AS resourceId, count(*) AS total, count(*) FILTER (WHERE NOT success) AS failures,
                   avg(response_time_ms) FILTER (WHERE success) AS avgResponseMs,
                   max(response_time_ms) FILTER (WHERE success) AS maxResponseMs,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY response_time_ms) FILTER (WHERE success) AS p95ResponseMs
              FROM health_check_executions
             WHERE organization_id = :orgId AND started_at >= :from AND started_at < :to
             GROUP BY resource_id
            """, nativeQuery = true)
    List<ResourceLatencyStats> latencyStatsPerResource(@Param("orgId") UUID organizationId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            SELECT * FROM health_check_executions
             WHERE organization_id = :orgId AND started_at >= :from AND started_at < :to
             ORDER BY started_at DESC LIMIT :limit
            """, nativeQuery = true)
    List<HealthCheckExecution> findRecentForOrganization(@Param("orgId") UUID organizationId, @Param("from") Instant from,
                                                         @Param("to") Instant to, @Param("limit") int limit);

    @Query("select r.id as id, r.name as name from MonitoredResource r where r.organizationId = :orgId")
    List<IdName> resourceNameRows(@Param("orgId") UUID organizationId);

    interface IdName { UUID getId(); String getName(); }

    default java.util.Map<UUID, String> resourceNames(UUID organizationId) {
        java.util.Map<UUID, String> m = new java.util.HashMap<>();
        for (IdName r : resourceNameRows(organizationId)) m.put(r.getId(), r.getName());
        return m;
    }

    interface ResourceAverage {
        UUID getResourceId();
        Double getAvgResponseMs();
        long getTotal();
    }

    @Query(value = """
            SELECT date_bin(make_interval(secs => :bucketSeconds), started_at, TIMESTAMPTZ '2000-01-01') AS bucket,
                   count(*) AS total,
                   count(*) FILTER (WHERE success) AS successes,
                   avg(response_time_ms) FILTER (WHERE success) AS avgResponseMs,
                   max(response_time_ms) FILTER (WHERE success) AS maxResponseMs
              FROM health_check_executions
             WHERE organization_id = :orgId AND started_at >= :from AND started_at < :to
             GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<BucketStats> bucketStatsForOrganization(@Param("orgId") UUID organizationId, @Param("from") Instant from,
                                                 @Param("to") Instant to, @Param("bucketSeconds") int bucketSeconds);

    @Query(value = """
            SELECT resource_id AS resourceId,
                   date_bin(make_interval(secs => :bucketSeconds), started_at, TIMESTAMPTZ '2000-01-01') AS bucket,
                   count(*) AS total,
                   count(*) FILTER (WHERE success) AS successes,
                   avg(response_time_ms) FILTER (WHERE success) AS avgResponseMs,
                   max(response_time_ms) FILTER (WHERE success) AS maxResponseMs
              FROM health_check_executions
             WHERE organization_id = :orgId AND started_at >= :from AND started_at < :to
             GROUP BY resource_id, bucket ORDER BY bucket
            """, nativeQuery = true)
    List<ResourceBucketStats> bucketStatsPerResource(@Param("orgId") UUID organizationId, @Param("from") Instant from,
                                                     @Param("to") Instant to, @Param("bucketSeconds") int bucketSeconds);

    @Query(value = """
            SELECT date_bin(make_interval(secs => :bucketSeconds), started_at, TIMESTAMPTZ '2000-01-01') AS bucket,
                   count(*) AS total,
                   count(*) FILTER (WHERE success) AS successes,
                   avg(response_time_ms) FILTER (WHERE success) AS avgResponseMs,
                   max(response_time_ms) FILTER (WHERE success) AS maxResponseMs
              FROM health_check_executions
             WHERE resource_id = :resourceId AND organization_id = :orgId AND started_at >= :from AND started_at < :to
             GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<BucketStats> bucketStatsForResource(@Param("resourceId") UUID resourceId, @Param("orgId") UUID organizationId,
                                             @Param("from") Instant from, @Param("to") Instant to,
                                             @Param("bucketSeconds") int bucketSeconds);

    @Query(value = """
            SELECT resource_id AS resourceId, avg(response_time_ms) AS avgResponseMs, count(*) AS total
              FROM health_check_executions
             WHERE organization_id = :orgId AND success AND started_at >= :from AND started_at < :to
             GROUP BY resource_id
            """, nativeQuery = true)
    List<ResourceAverage> averagePerResource(@Param("orgId") UUID organizationId, @Param("from") Instant from,
                                             @Param("to") Instant to);

    @Query(value = """
            SELECT avg(response_time_ms) FROM health_check_executions
             WHERE organization_id = :orgId AND success AND started_at >= :from AND started_at < :to
            """, nativeQuery = true)
    Double averageResponseTime(@Param("orgId") UUID organizationId, @Param("from") Instant from, @Param("to") Instant to);

    @Modifying
    @Query(value = "DELETE FROM health_check_executions WHERE started_at < :threshold", nativeQuery = true)
    int deleteOlderThan(@Param("threshold") Instant threshold);
}
