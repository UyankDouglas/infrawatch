package com.infrawatch.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    @EntityGraph(attributePaths = "organization")
    List<OrganizationMember> findByUserIdOrderByCreatedAtAsc(UUID userId);

    @EntityGraph(attributePaths = "organization")
    Optional<OrganizationMember> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    @EntityGraph(attributePaths = "user")
    List<OrganizationMember> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    long countByOrganizationIdAndRole(UUID organizationId, MemberRole role);
}
