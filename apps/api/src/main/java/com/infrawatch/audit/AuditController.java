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

    public record AuditEntry(UUID id, UUID userId, String userName, String action, String entityType, UUID entityId,
                             String details, String ipAddress, Instant createdAt) {}

    private final AuditLogRepository repository;
    private final CurrentContextService context;
    private final com.infrawatch.auth.UserRepository users;

    public AuditController(AuditLogRepository repository, CurrentContextService context, com.infrawatch.auth.UserRepository users) {
        this.repository = repository;
        this.context = context;
        this.users = users;
    }

    @GetMapping
    public PageResponse<AuditEntry> list(@RequestParam(required = false) String action,
                                         @RequestParam(required = false) UUID userId,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        OrganizationContext ctx = context.require();
        ctx.require(MemberRole.ADMIN);
        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, q, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> p = new java.util.ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), ctx.organizationId()));
            if (action != null && !action.isBlank()) p.add(cb.like(root.get("action"), action.trim() + "%"));
            if (userId != null) p.add(cb.equal(root.get("userId"), userId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            if (search != null && !search.isBlank()) p.add(cb.like(cb.lower(root.get("details")), "%" + search.trim().toLowerCase() + "%"));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        var result = repository.findAll(spec, pageable).map(a -> new AuditEntry(a.getId(), a.getUserId(),
                a.getUserId() == null ? null : names.computeIfAbsent(a.getUserId(), id -> users.findById(id).map(com.infrawatch.auth.User::getFullName).orElse("Usuário removido")),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt()));
        return PageResponse.of(result);
    }

    @GetMapping("/actions")
    public java.util.List<String> actions() {
        OrganizationContext ctx = context.require();
        ctx.require(MemberRole.ADMIN);
        return repository.findAll((root, q, cb) -> cb.equal(root.get("organizationId"), ctx.organizationId())).stream()
                .map(AuditLog::getAction).distinct().sorted().toList();
    }
}
