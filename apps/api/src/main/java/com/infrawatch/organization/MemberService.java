package com.infrawatch.organization;

import com.infrawatch.audit.AuditService;
import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.common.ApiException;
import com.infrawatch.integrations.IntegrationsProperties;
import com.infrawatch.metrics.AgentTokens;
import com.infrawatch.security.AppUserDetails;
import com.infrawatch.security.OrganizationContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de membros e convites. Protege o último administrador e nunca confia em IDs de organização do cliente. */
@Service
public class MemberService {

    public record InviteResponse(UUID id, String email, MemberRole role, String roleLabel, String status, Instant expiresAt,
                                 String invitedBy, Instant createdAt) {}

    public record InviteCreated(InviteResponse invite, String token, String link, String message) {}

    public record InvitePublic(String organizationName, String email, MemberRole role, String roleLabel, boolean valid, String reason) {}

    private final OrganizationMemberRepository members;
    private final OrganizationRepository organizations;
    private final OrganizationInviteRepository invites;
    private final UserRepository users;
    private final AuditService audit;
    private final IntegrationsProperties integrations;

    public MemberService(OrganizationMemberRepository members, OrganizationRepository organizations,
                         OrganizationInviteRepository invites, UserRepository users, AuditService audit,
                         IntegrationsProperties integrations) {
        this.members = members;
        this.organizations = organizations;
        this.invites = invites;
        this.users = users;
        this.audit = audit;
        this.integrations = integrations;
    }

    @Transactional
    public OrganizationMember changeRole(OrganizationContext ctx, UUID userId, MemberRole role) {
        ctx.require(MemberRole.ADMIN);
        OrganizationMember m = members.findByUserIdAndOrganizationId(userId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Membro não encontrado."));
        if (m.getRole() == MemberRole.ADMIN && role != MemberRole.ADMIN && members.countByOrganizationIdAndRole(ctx.organizationId(), MemberRole.ADMIN) <= 1) {
            throw ApiException.conflict("A organização precisa manter ao menos um administrador.");
        }
        MemberRole previous = m.getRole();
        m.setRole(role);
        audit.record(ctx.organizationId(), ctx.userId(), "member.role_changed", "user", userId,
                m.getUser().getEmail() + ": " + previous.getLabel() + " → " + role.getLabel());
        return m;
    }

    @Transactional
    public void remove(OrganizationContext ctx, UUID userId) {
        ctx.require(MemberRole.ADMIN);
        OrganizationMember m = members.findByUserIdAndOrganizationId(userId, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Membro não encontrado."));
        if (m.getRole() == MemberRole.ADMIN && members.countByOrganizationIdAndRole(ctx.organizationId(), MemberRole.ADMIN) <= 1) {
            throw ApiException.conflict("Não é possível remover o último administrador.");
        }
        members.delete(m);
        audit.record(ctx.organizationId(), ctx.userId(), "member.removed", "user", userId, m.getUser().getEmail() + " removido da organização");
    }

    @Transactional
    public InviteCreated invite(OrganizationContext ctx, String rawEmail, MemberRole role) {
        ctx.require(MemberRole.ADMIN);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(email).flatMap(u -> members.findByUserIdAndOrganizationId(u.getId(), ctx.organizationId()))
                .ifPresent(m -> { throw ApiException.conflict("Este e-mail já é membro da organização."); });
        AgentTokens.Generated g = AgentTokens.generate(); // mesmo gerador seguro: prefixo + segredo, hash SHA-256
        String token = g.token().replace("iwa_", "inv_");
        OrganizationInvite inv = invites.save(new OrganizationInvite(ctx.organizationId(), email, role,
                AgentTokens.hash(token), ctx.userId(), Instant.now().plus(Duration.ofDays(7))));
        audit.record(ctx.organizationId(), ctx.userId(), "member.invited", "organization_invite", inv.getId(),
                email + " convidado como " + role.getLabel());
        String link = integrations.baseUrl() + "/convite/" + token;
        return new InviteCreated(toResponse(inv), token, link,
                "Envie este link ao convidado. Ele expira em 7 dias e só pode ser usado uma vez.");
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listInvites(OrganizationContext ctx) {
        ctx.require(MemberRole.ADMIN);
        return invites.findByOrganizationIdOrderByCreatedAtDesc(ctx.organizationId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void revokeInvite(OrganizationContext ctx, UUID id) {
        ctx.require(MemberRole.ADMIN);
        OrganizationInvite inv = invites.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Convite não encontrado."));
        inv.revoke(Instant.now());
        audit.record(ctx.organizationId(), ctx.userId(), "member.invite_revoked", "organization_invite", id, inv.getEmail());
    }

    /** Informações públicas do convite (sem autenticação), para a tela de aceite. */
    @Transactional(readOnly = true)
    public InvitePublic describe(String token) {
        return invites.findByTokenHash(AgentTokens.hash(token)).map(inv -> {
            Organization org = organizations.findById(inv.getOrganizationId()).orElse(null);
            boolean valid = inv.isPending(Instant.now());
            return new InvitePublic(org == null ? null : org.getName(), inv.getEmail(), inv.getRole(), inv.getRole().getLabel(), valid,
                    valid ? null : switch (inv.status(Instant.now())) {
                        case "ACCEPTED" -> "Convite já utilizado.";
                        case "REVOKED" -> "Convite revogado.";
                        default -> "Convite expirado.";
                    });
        }).orElse(new InvitePublic(null, null, null, null, false, "Convite inválido."));
    }

    /** Aceita um convite para o usuário autenticado (o e-mail precisa coincidir). */
    @Transactional
    public Organization accept(AppUserDetails user, String token) {
        OrganizationInvite inv = invites.findByTokenHash(AgentTokens.hash(token))
                .filter(i -> i.isPending(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("invite_invalid", "Convite inválido, expirado ou já utilizado."));
        if (!inv.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw ApiException.forbidden("Este convite foi emitido para outro e-mail (" + inv.getEmail() + ").");
        }
        return join(inv, user.getId());
    }

    /** Usado pelo cadastro com convite: o usuário recém-criado entra na organização em vez de criar uma nova. */
    @Transactional
    public Organization acceptOnRegister(User user, String token) {
        OrganizationInvite inv = invites.findByTokenHash(AgentTokens.hash(token))
                .filter(i -> i.isPending(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("invite_invalid", "Convite inválido, expirado ou já utilizado."));
        if (!inv.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw ApiException.badRequest("invite_email_mismatch", "Cadastre-se com o e-mail para o qual o convite foi emitido (" + inv.getEmail() + ").");
        }
        return join(inv, user.getId());
    }

    private Organization join(OrganizationInvite inv, UUID userId) {
        Organization org = organizations.findById(inv.getOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Organização não encontrada."));
        User user = users.findById(userId).orElseThrow();
        if (members.findByUserIdAndOrganizationId(userId, org.getId()).isEmpty()) {
            members.save(new OrganizationMember(org, user, inv.getRole()));
        }
        inv.accept(userId, Instant.now());
        audit.record(org.getId(), userId, "member.joined", "user", userId, user.getEmail() + " entrou como " + inv.getRole().getLabel());
        return org;
    }

    private InviteResponse toResponse(OrganizationInvite inv) {
        String by = inv.getInvitedBy() == null ? null : users.findById(inv.getInvitedBy()).map(User::getFullName).orElse(null);
        return new InviteResponse(inv.getId(), inv.getEmail(), inv.getRole(), inv.getRole().getLabel(), inv.status(Instant.now()),
                inv.getExpiresAt(), by, inv.getCreatedAt());
    }
}
