package com.infrawatch.organization;

import com.infrawatch.organization.MemberService.InviteCreated;
import com.infrawatch.organization.MemberService.InvitePublic;
import com.infrawatch.organization.MemberService.InviteResponse;
import com.infrawatch.security.CurrentContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MemberController {

    public record RoleRequest(@NotNull MemberRole role) {}
    public record InviteRequest(@NotBlank @Email(message = "E-mail inválido.") String email, @NotNull MemberRole role) {}

    private final MemberService service;
    private final CurrentContextService context;

    public MemberController(MemberService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @PatchMapping("/organizations/current/members/{userId}")
    public Map<String, Object> changeRole(@PathVariable UUID userId, @Valid @RequestBody RoleRequest body) {
        OrganizationMember m = service.changeRole(context.require(), userId, body.role());
        return Map.of("userId", userId, "role", m.getRole(), "roleLabel", m.getRole().getLabel());
    }

    @DeleteMapping("/organizations/current/members/{userId}")
    public ResponseEntity<Void> remove(@PathVariable UUID userId) {
        service.remove(context.require(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/organizations/current/invites")
    public List<InviteResponse> invites() {
        return service.listInvites(context.require());
    }

    @PostMapping("/organizations/current/invites")
    public ResponseEntity<InviteCreated> invite(@Valid @RequestBody InviteRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.invite(context.require(), body.email(), body.role()));
    }

    @DeleteMapping("/organizations/current/invites/{id}")
    public ResponseEntity<Void> revokeInvite(@PathVariable UUID id) {
        service.revokeInvite(context.require(), id);
        return ResponseEntity.noContent().build();
    }

    /** Público: descreve o convite para a tela de aceite. */
    @GetMapping("/invites/{token}")
    public InvitePublic describe(@PathVariable String token) {
        return service.describe(token);
    }

    /** Autenticado: aceita o convite para o usuário logado. */
    @PostMapping("/invites/{token}/accept")
    public Map<String, Object> accept(@PathVariable String token) {
        Organization org = service.accept(context.requireUser(), token);
        context.switchTo(org.getId());
        return Map.of("organizationId", org.getId(), "organizationName", org.getName());
    }
}
