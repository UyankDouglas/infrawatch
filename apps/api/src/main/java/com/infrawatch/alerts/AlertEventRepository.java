package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID>, JpaSpecificationExecutor<AlertEvent> {

    Optional<AlertEvent> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select e from AlertEvent e where e.ruleId = :ruleId and e.resourceId = :resourceId and e.status <> com.infrawatch.alerts.AlertEnums.Status.RESOLVED")
    Optional<AlertEvent> findActive(@Param("ruleId") UUID ruleId, @Param("resourceId") UUID resourceId);

    @Query("select e from AlertEvent e where e.resourceId = :resourceId and e.metric = :metric and e.status <> com.infrawatch.alerts.AlertEnums.Status.RESOLVED")
    List<AlertEvent> findActiveByResourceAndMetric(@Param("resourceId") UUID resourceId, @Param("metric") Metric metric);

    @Query("select e from AlertEvent e where e.ruleId = :ruleId and e.status <> com.infrawatch.alerts.AlertEnums.Status.RESOLVED")
    List<AlertEvent> findActiveByRule(@Param("ruleId") UUID ruleId);

    @Query("select e from AlertEvent e where e.resourceId = :resourceId and e.status <> com.infrawatch.alerts.AlertEnums.Status.RESOLVED order by e.triggeredAt desc")
    List<AlertEvent> findActiveByResource(@Param("resourceId") UUID resourceId);

    long countByOrganizationIdAndStatusIn(UUID organizationId, List<Status> statuses);

    List<AlertEvent> findTop10ByOrganizationIdOrderByTriggeredAtDesc(UUID organizationId);
}
