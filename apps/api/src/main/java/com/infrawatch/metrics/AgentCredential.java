package com.infrawatch.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Credencial do agente: apenas o hash SHA-256 do token é armazenado; o prefixo identifica a credencial. */
@Entity
@Table(name = "agent_credentials")
public class AgentCredential {

    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected AgentCredential() {}

    public AgentCredential(UUID agentId, String tokenPrefix, String tokenHash) {
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.tokenPrefix = tokenPrefix;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public String getTokenPrefix() { return tokenPrefix; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public boolean isActive() { return revokedAt == null; }

    public void revoke() { this.revokedAt = Instant.now(); }
    public void used(Instant at) { this.lastUsedAt = at; }
}
