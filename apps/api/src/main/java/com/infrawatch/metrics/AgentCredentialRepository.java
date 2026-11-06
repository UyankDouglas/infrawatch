package com.infrawatch.metrics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCredentialRepository extends JpaRepository<AgentCredential, UUID> {
    Optional<AgentCredential> findByTokenPrefix(String tokenPrefix);
    List<AgentCredential> findByAgentIdAndRevokedAtIsNull(UUID agentId);
}
