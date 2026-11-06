package com.infrawatch.maintenance;

import com.infrawatch.maintenance.MaintenanceWindow.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, UUID> {

    Optional<MaintenanceWindow> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<MaintenanceWindow> findByOrganizationIdOrderByStartsAtDesc(UUID organizationId);

    List<MaintenanceWindow> findByStatusAndStartsAtLessThanEqual(Status status, Instant now);

    List<MaintenanceWindow> findByStatusAndEndsAtLessThanEqual(Status status, Instant now);

    @Query("select w from MaintenanceWindow w where w.organizationId = :orgId and w.status in (com.infrawatch.maintenance.MaintenanceWindow$Status.SCHEDULED, com.infrawatch.maintenance.MaintenanceWindow$Status.ACTIVE) order by w.startsAt asc")
    List<MaintenanceWindow> findUpcomingOrActive(@Param("orgId") UUID organizationId);

    @Query("select w from MaintenanceWindow w join w.resourceIds r where r = :resourceId and w.status = com.infrawatch.maintenance.MaintenanceWindow$Status.ACTIVE")
    List<MaintenanceWindow> findActiveForResource(@Param("resourceId") UUID resourceId);

    @Query("select w from MaintenanceWindow w join w.resourceIds r where r = :resourceId and w.status in (com.infrawatch.maintenance.MaintenanceWindow$Status.SCHEDULED, com.infrawatch.maintenance.MaintenanceWindow$Status.ACTIVE) order by w.startsAt asc")
    List<MaintenanceWindow> findUpcomingOrActiveForResource(@Param("resourceId") UUID resourceId);

    @Query("select w from MaintenanceWindow w where w.organizationId = :orgId and w.status in (com.infrawatch.maintenance.MaintenanceWindow$Status.ACTIVE, com.infrawatch.maintenance.MaintenanceWindow$Status.COMPLETED) and w.endsAt >= :from and w.startsAt <= :to")
    List<MaintenanceWindow> findOverlapping(@Param("orgId") UUID organizationId, @Param("from") Instant from, @Param("to") Instant to);
}
