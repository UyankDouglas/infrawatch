package com.infrawatch.auth;

import com.infrawatch.audit.AuditService;
import com.infrawatch.auth.AuthDtos.MeResponse;
import com.infrawatch.auth.AuthDtos.OrganizationSummary;
import com.infrawatch.auth.AuthDtos.RegisterRequest;
import com.infrawatch.common.ApiException;
import com.infrawatch.common.Slugs;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.organization.Organization;
import com.infrawatch.organization.OrganizationMember;
import com.infrawatch.organization.OrganizationMemberRepository;
import com.infrawatch.organization.OrganizationRepository;
import com.infrawatch.security.AppUserDetails;
import com.infrawatch.security.CurrentContextService;
import com.infrawatch.security.OrganizationContext;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final CurrentContextService context;

    public AuthService(UserRepository users, OrganizationRepository organizations,
                       OrganizationMemberRepository members, PasswordEncoder passwordEncoder,
                       AuditService audit, CurrentContextService context) {
        this.users = users;
        this.organizations = organizations;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.context = context;
    }

    /** Cria o usuário e sua primeira organização, na qual ele é administrador. */
    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Já existe uma conta com este e-mail.");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().trim()));
        Organization organization = organizations.save(
                new Organization(request.organizationName().trim(), uniqueSlug(request.organizationName())));
        members.save(new OrganizationMember(organization, user, MemberRole.ADMIN));
        audit.record(organization.getId(), user.getId(), "auth.register", "user", user.getId(),
                "Conta criada e organização \"" + organization.getName() + "\" registrada");
        return user;
    }

    @Transactional
    public void recordLogin(UUID userId) {
        users.findById(userId).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            audit.record(null, userId, "auth.login", "user", userId, "Login realizado");
        });
    }

    @Transactional(readOnly = true)
    public MeResponse me(AppUserDetails principal) {
        User user = users.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("Sessão inválida."));
        List<OrganizationSummary> orgs = members.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(m -> new OrganizationSummary(m.getOrganization().getId(), m.getOrganization().getName(),
                        m.getOrganization().getSlug(), m.getRole(), m.getRole().getLabel()))
                .toList();
        OrganizationContext current = context.require();
        OrganizationSummary currentSummary = orgs.stream()
                .filter(o -> o.id().equals(current.organizationId()))
                .findFirst().orElse(null);
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt(),
                currentSummary, orgs);
    }

    private String uniqueSlug(String name) {
        String base = Slugs.of(name);
        String slug = base;
        int attempt = 1;
        while (organizations.existsBySlug(slug)) {
            slug = base + "-" + (++attempt);
        }
        return slug;
    }
}
