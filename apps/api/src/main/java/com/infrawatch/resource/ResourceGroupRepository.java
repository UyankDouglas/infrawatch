package com.infrawatch.resource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceGroupRepository extends JpaRepository<ResourceGroup, UUID> {
    List<ResourceGroup> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<ResourceGroup> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}
