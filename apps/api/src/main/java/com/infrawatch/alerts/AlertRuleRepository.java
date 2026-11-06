package com.infrawatch.alerts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
    List<AlertRule> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
    List<AlertRule> findByOrganizationIdAndEnabledTrue(UUID organizationId);
    Optional<AlertRule> findByIdAndOrganizationId(UUID id, UUID organizationId);
    long countByOrganizationId(UUID organizationId);
}
