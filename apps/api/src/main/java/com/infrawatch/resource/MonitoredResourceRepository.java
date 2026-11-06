package com.infrawatch.resource;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitoredResourceRepository extends JpaRepository<MonitoredResource, UUID>,
        JpaSpecificationExecutor<MonitoredResource> {

    Optional<MonitoredResource> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    Page<MonitoredResource> findByOrganizationId(UUID organizationId, Pageable pageable);

    /** Carga em lote (dashboard/relatórios): traz grupo e configuração no mesmo SELECT; tags vêm por @BatchSize. */
    @EntityGraph(attributePaths = {"group", "checkConfig"})
    List<MonitoredResource> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndCurrentStatus(UUID organizationId, ResourceStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MonitoredResource r where r.id = :id")
    Optional<MonitoredResource> lockById(@Param("id") UUID id);

    /**
     * Reivindica recursos com verificação pendente de forma atômica: o próprio UPDATE adianta o next_check_at,
     * portanto duas execuções do despachante (ou duas instâncias) não escolhem o mesmo recurso.
     */
    @Query(value = """
            UPDATE monitored_resources r
               SET next_check_at = now() + make_interval(secs => c.interval_seconds)
              FROM health_check_configs c
             WHERE c.resource_id = r.id
               AND r.id IN (SELECT r2.id FROM monitored_resources r2
                             WHERE r2.enabled = TRUE AND r2.next_check_at <= :now
                               AND r2.type IN ('HTTP','HTTPS','HEALTH_ENDPOINT')
                             ORDER BY r2.next_check_at
                             LIMIT :limit FOR UPDATE SKIP LOCKED)
            RETURNING r.id
            """, nativeQuery = true)
    List<UUID> claimDue(@Param("now") Instant now, @Param("limit") int limit);

    @Query("""
            select r from MonitoredResource r
             where r.enabled = true and r.currentStatus in (com.infrawatch.resource.ResourceStatus.UP,
                   com.infrawatch.resource.ResourceStatus.DOWN, com.infrawatch.resource.ResourceStatus.DEGRADED)
               and r.lastCheckAt is not null and r.lastCheckAt < :threshold
            """)
    List<MonitoredResource> findStale(@Param("threshold") Instant threshold);

    @Query("select r from MonitoredResource r where r.organizationId = :orgId and r.currentStatus in :statuses order by r.statusSince desc")
    List<MonitoredResource> findByOrganizationIdAndStatusIn(@Param("orgId") UUID organizationId,
                                                            @Param("statuses") List<ResourceStatus> statuses);

    @Query("select r.type as type, count(r) as total from MonitoredResource r where r.organizationId = :orgId group by r.type")
    List<TypeCount> countByType(@Param("orgId") UUID organizationId);

    interface TypeCount {
        ResourceType getType();
        long getTotal();
    }
}
