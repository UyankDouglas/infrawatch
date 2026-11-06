package com.infrawatch.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceStatusHistoryRepository extends JpaRepository<ResourceStatusHistory, UUID> {

    Page<ResourceStatusHistory> findByResourceIdAndOrganizationIdOrderByChangedAtDesc(UUID resourceId,
                                                                                      UUID organizationId,
                                                                                      Pageable pageable);

    List<ResourceStatusHistory> findByOrganizationIdAndChangedAtGreaterThanEqualOrderByChangedAtAsc(
            UUID organizationId, Instant from);

    List<ResourceStatusHistory> findByResourceIdAndChangedAtGreaterThanEqualOrderByChangedAtAsc(
            UUID resourceId, Instant from);

    List<ResourceStatusHistory> findTop20ByOrganizationIdOrderByChangedAtDesc(UUID organizationId);
}
