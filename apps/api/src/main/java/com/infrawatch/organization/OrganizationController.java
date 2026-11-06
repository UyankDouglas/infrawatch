package com.infrawatch.organization;

import com.infrawatch.auth.AuthDtos.OrganizationSummary;
import com.infrawatch.security.CurrentContextService;
import com.infrawatch.security.OrganizationContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    public record MemberResponse(UUID userId, String fullName, String email, MemberRole role, String roleLabel,
                                 Instant since) {}

    private final CurrentContextService context;
    private final OrganizationMemberRepository members;

    public OrganizationController(CurrentContextService context, OrganizationMemberRepository members) {
        this.context = context;
        this.members = members;
    }

    @GetMapping("/current")
    public OrganizationSummary current() {
        OrganizationContext ctx = context.require();
        return new OrganizationSummary(ctx.organizationId(), ctx.organizationName(), null, ctx.role(),
                ctx.role().getLabel());
    }

    @GetMapping("/current/members")
    public List<MemberResponse> members() {
        OrganizationContext ctx = context.require();
        return members.findByOrganizationIdOrderByCreatedAtAsc(ctx.organizationId()).stream()
                .map(m -> new MemberResponse(m.getUser().getId(), m.getUser().getFullName(), m.getUser().getEmail(),
                        m.getRole(), m.getRole().getLabel(), m.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{id}/switch")
    public OrganizationSummary switchTo(@PathVariable UUID id) {
        OrganizationContext ctx = context.switchTo(id);
        return new OrganizationSummary(ctx.organizationId(), ctx.organizationName(), null, ctx.role(),
                ctx.role().getLabel());
    }
}
