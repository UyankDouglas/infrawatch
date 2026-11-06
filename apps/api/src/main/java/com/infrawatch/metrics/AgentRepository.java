package com.infrawatch.metrics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, UUID> {
    Optional<Agent> findByResourceId(UUID resourceId);
    Optional<Agent> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<Agent> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
    List<Agent> findByStatus(Agent.Status status);
}
