package com.infrawatch.audit;

import com.infrawatch.common.PageResponse;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.security.CurrentContextService;
import com.infrawatch.security.OrganizationContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    public record AuditEntry(UUID id, UUID userId, String action, String entityType, UUID entityId,
                             String details, String ipAddress, Instant createdAt) {}

    private final AuditLogRepository repository;
    private final CurrentContextService context;

    public AuditController(AuditLogRepository repository, CurrentContextService context) {
        this.repository = repository;
        this.context = context;
    }

    @GetMapping
    public PageResponse<AuditEntry> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        OrganizationContext ctx = context.require();
        ctx.require(MemberRole.ADMIN);
        var result = repository.findByOrganizationIdOrderByCreatedAtDesc(ctx.organizationId(),
                        PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)))
                .map(a -> new AuditEntry(a.getId(), a.getUserId(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt()));
        return PageResponse.of(result);
    }
}
