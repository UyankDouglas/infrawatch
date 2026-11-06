package com.infrawatch.notifications;

import com.infrawatch.common.ApiException;
import com.infrawatch.common.PageResponse;
import com.infrawatch.notifications.NotificationService.NotificationDto;
import com.infrawatch.security.CurrentContextService;
import com.infrawatch.security.OrganizationContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository repository;
    private final CurrentContextService context;

    public NotificationController(NotificationRepository repository, CurrentContextService context) {
        this.repository = repository;
        this.context = context;
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        OrganizationContext ctx = context.require();
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100));
        var result = unreadOnly
                ? repository.findByUserIdAndOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(ctx.userId(), ctx.organizationId(), pageable)
                : repository.findByUserIdAndOrganizationIdOrderByCreatedAtDesc(ctx.userId(), ctx.organizationId(), pageable);
        return PageResponse.of(result.map(NotificationDto::of));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        OrganizationContext ctx = context.require();
        return Map.of("unread", repository.countByUserIdAndOrganizationIdAndReadAtIsNull(ctx.userId(), ctx.organizationId()));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public NotificationDto markRead(@PathVariable UUID id) {
        OrganizationContext ctx = context.require();
        Notification n = repository.findByIdAndUserId(id, ctx.userId())
                .orElseThrow(() -> ApiException.notFound("Notificação não encontrada."));
        n.markRead(Instant.now());
        return NotificationDto.of(n);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead() {
        OrganizationContext ctx = context.require();
        return Map.of("updated", repository.markAllRead(ctx.userId(), ctx.organizationId(), Instant.now()));
    }
}
