package com.infrawatch.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Convite para entrar em uma organização; o token é exibido uma vez e guardado como hash. */
@Entity
@Table(name = "organization_invites")
public class OrganizationInvite {

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 255) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MemberRole role;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "accepted_by") private UUID acceptedBy;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected OrganizationInvite() {}

    public OrganizationInvite(UUID organizationId, String email, MemberRole role, String tokenHash, UUID invitedBy, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isPending(Instant now) { return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now); }
    public String status(Instant now) {
        if (acceptedAt != null) return "ACCEPTED";
        if (revokedAt != null) return "REVOKED";
        return expiresAt.isAfter(now) ? "PENDING" : "EXPIRED";
    }
    public void accept(UUID userId, Instant at) { this.acceptedAt = at; this.acceptedBy = userId; }
    public void revoke(Instant at) { this.revokedAt = at; }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEmail() { return email; }
    public MemberRole getRole() { return role; }
    public String getTokenHash() { return tokenHash; }
    public UUID getInvitedBy() { return invitedBy; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public UUID getAcceptedBy() { return acceptedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
