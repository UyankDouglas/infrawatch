package com.infrawatch.auth;

import com.infrawatch.organization.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Informe o nome completo.") @Size(min = 2, max = 150) String fullName,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 255) String email,
            @NotBlank(message = "Informe a senha.") @Size(min = 10, max = 128, message = "A senha deve ter entre 10 e 128 caracteres.") String password,
            @NotBlank(message = "Informe o nome da organização.") @Size(min = 2, max = 150) String organizationName) {}

    public record LoginRequest(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") String email,
            @NotBlank(message = "Informe a senha.") String password) {}

    public record OrganizationSummary(UUID id, String name, String slug, MemberRole role, String roleLabel) {}

    public record MeResponse(UUID id, String email, String fullName, Instant createdAt,
                             OrganizationSummary currentOrganization, List<OrganizationSummary> organizations) {}

    public record CsrfResponse(String headerName, String cookieName) {}
}
