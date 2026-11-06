package com.infrawatch.security;

import com.infrawatch.common.ApiException;
import com.infrawatch.organization.OrganizationMember;
import com.infrawatch.organization.OrganizationMemberRepository;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolve a organização ativa a partir do contexto autenticado.
 * O identificador da organização nunca é aceito do frontend sem validar a associação do usuário.
 */
@Service
public class CurrentContextService {

    public static final String SESSION_ORG_KEY = "infrawatch.currentOrganizationId";

    private final OrganizationMemberRepository members;

    public CurrentContextService(OrganizationMemberRepository members) {
        this.members = members;
    }

    public Optional<AppUserDetails> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserDetails details) {
            return Optional.of(details);
        }
        return Optional.empty();
    }

    public AppUserDetails requireUser() {
        return currentUser().orElseThrow(() -> ApiException.unauthorized("Autenticação necessária."));
    }

    @Transactional(readOnly = true)
    public OrganizationContext require() {
        AppUserDetails user = requireUser();
        List<OrganizationMember> memberships = members.findByUserIdOrderByCreatedAtAsc(user.getId());
        if (memberships.isEmpty()) {
            throw ApiException.forbidden("Usuário não pertence a nenhuma organização.");
        }
        UUID selected = (UUID) session().map(s -> s.getAttribute(SESSION_ORG_KEY)).orElse(null);
        OrganizationMember membership = memberships.stream()
                .filter(m -> m.getOrganization().getId().equals(selected))
                .findFirst()
                .orElse(memberships.getFirst());
        if (!membership.getOrganization().getId().equals(selected)) {
            session().ifPresent(s -> s.setAttribute(SESSION_ORG_KEY, membership.getOrganization().getId()));
        }
        return new OrganizationContext(user.getId(), user.getEmail(), membership.getOrganization().getId(),
                membership.getOrganization().getName(), membership.getRole());
    }

    @Transactional(readOnly = true)
    public OrganizationContext switchTo(UUID organizationId) {
        AppUserDetails user = requireUser();
        OrganizationMember membership = members.findByUserIdAndOrganizationId(user.getId(), organizationId)
                .orElseThrow(() -> ApiException.forbidden("Você não é membro desta organização."));
        session().ifPresent(s -> s.setAttribute(SESSION_ORG_KEY, organizationId));
        return new OrganizationContext(user.getId(), user.getEmail(), membership.getOrganization().getId(),
                membership.getOrganization().getName(), membership.getRole());
    }

    private Optional<HttpSession> session() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.ofNullable(attrs.getRequest().getSession(false));
        }
        return Optional.empty();
    }
}
