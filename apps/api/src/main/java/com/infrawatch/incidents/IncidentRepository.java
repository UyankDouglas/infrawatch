package com.infrawatch.incidents;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select coalesce(max(i.number), 0) from Incident i where i.organizationId = :orgId")
    long maxNumber(@Param("orgId") UUID organizationId);

    @Query("select i from Incident i where i.resourceId = :resourceId and i.origin = com.infrawatch.incidents.Incident$Origin.AUTOMATIC and i.status not in (com.infrawatch.incidents.Incident$Status.RESOLVED, com.infrawatch.incidents.Incident$Status.CLOSED)")
    Optional<Incident> findActiveAutomatic(@Param("resourceId") UUID resourceId);

    @Query("select i from Incident i where i.organizationId = :orgId and i.status not in (com.infrawatch.incidents.Incident$Status.RESOLVED, com.infrawatch.incidents.Incident$Status.CLOSED) order by i.openedAt desc")
    List<Incident> findActive(@Param("orgId") UUID organizationId);

    @Query("select i from Incident i where i.resourceId = :resourceId and i.status not in (com.infrawatch.incidents.Incident$Status.RESOLVED, com.infrawatch.incidents.Incident$Status.CLOSED) order by i.openedAt desc")
    List<Incident> findActiveByResource(@Param("resourceId") UUID resourceId);

    long countByOrganizationIdAndStatusIn(UUID organizationId, List<Incident.Status> statuses);

    List<Incident> findTop10ByOrganizationIdOrderByOpenedAtDesc(UUID organizationId);

    @Query(value = """
            SELECT date_bin(make_interval(secs => :bucketSeconds), opened_at, TIMESTAMPTZ '2000-01-01') AS bucket,
                   count(*) AS total,
                   count(*) FILTER (WHERE origin = 'AUTOMATIC') AS automatic
              FROM incidents
             WHERE organization_id = :orgId AND opened_at >= :from AND opened_at < :to
             GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<IncidentBucket> openedPerBucket(@Param("orgId") UUID organizationId, @Param("from") Instant from,
                                         @Param("to") Instant to, @Param("bucketSeconds") int bucketSeconds);

    interface IncidentBucket {
        Instant getBucket();
        long getTotal();
        long getAutomatic();
    }
}
