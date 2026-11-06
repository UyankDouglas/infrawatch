package com.infrawatch.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {
    Optional<OrganizationInvite> findByTokenHash(String tokenHash);
    List<OrganizationInvite> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    Optional<OrganizationInvite> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
