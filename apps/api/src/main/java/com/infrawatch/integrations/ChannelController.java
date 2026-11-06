package com.infrawatch.integrations;

import com.infrawatch.common.PageResponse;
import com.infrawatch.integrations.IntegrationDtos.ChannelRequest;
import com.infrawatch.integrations.IntegrationDtos.ChannelResponse;
import com.infrawatch.integrations.IntegrationDtos.DeliveryResponse;
import com.infrawatch.notifications.Notification;
import com.infrawatch.security.CurrentContextService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class ChannelController {

    private final ChannelService service;
    private final CurrentContextService context;

    public ChannelController(ChannelService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping("/channels")
    public List<ChannelResponse> list() {
        return service.list(context.require());
    }

    @GetMapping("/channels/options")
    public Map<String, Object> options() {
        context.require();
        return Map.of("eventTypes", List.of(Notification.Type.values()).stream()
                .map(t -> Map.of("value", t.name(), "label", t.getLabel())).toList());
    }

    @PostMapping("/channels")
    public ResponseEntity<ChannelResponse> create(@Valid @RequestBody ChannelRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(context.require(), body));
    }

    @PatchMapping("/channels/{id}")
    public ChannelResponse update(@PathVariable UUID id, @RequestBody ChannelRequest body) {
        return service.update(context.require(), id, body);
    }

    @DeleteMapping("/channels/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(context.require(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/channels/{id}/test")
    public DeliveryResponse test(@PathVariable UUID id) {
        return service.test(context.require(), id);
    }

    @GetMapping("/deliveries")
    public PageResponse<DeliveryResponse> deliveries(@RequestParam(required = false) UUID channelId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(service.deliveries(context.require(), channelId, PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100))));
    }
}
