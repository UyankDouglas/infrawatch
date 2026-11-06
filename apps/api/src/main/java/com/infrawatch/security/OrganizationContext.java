package com.infrawatch.security;

import com.infrawatch.organization.MemberRole;
import java.util.UUID;

/** Contexto resolvido no servidor: usuário autenticado + organização ativa + papel nela. */
public record OrganizationContext(UUID userId, String userEmail, UUID organizationId, String organizationName,
                                  MemberRole role) {

    public void require(MemberRole minimum) {
        if (!role.atLeast(minimum)) {
            throw com.infrawatch.common.ApiException.forbidden(
                    "Esta ação exige o perfil " + minimum.getLabel() + " ou superior.");
        }
    }
}
