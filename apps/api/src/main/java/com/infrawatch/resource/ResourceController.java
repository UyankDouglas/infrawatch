package com.infrawatch.resource;

import com.infrawatch.common.PageResponse;
import com.infrawatch.resource.ResourceDtos.CreateResourceRequest;
import com.infrawatch.resource.ResourceDtos.ResourceResponse;
import com.infrawatch.resource.ResourceDtos.UpdateResourceRequest;
import com.infrawatch.resource.ResourceService.Filters;
import com.infrawatch.security.CurrentContextService;
import com.infrawatch.security.OrganizationContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name", "status", "currentStatus", "lastCheckAt", "lastCheckAt",
            "responseTime", "lastResponseTimeMs", "createdAt", "createdAt", "environment", "environment");

    private final ResourceService service;
    private final CurrentContextService context;

    public ResourceController(ResourceService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping
    public PageResponse<ResourceResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) ResourceStatus status,
                                               @RequestParam(required = false) ResourceType type,
                                               @RequestParam(required = false) Environment environment,
                                               @RequestParam(required = false) UUID groupId,
                                               @RequestParam(required = false) Boolean enabled,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(defaultValue = "name") String sort,
                                               @RequestParam(defaultValue = "asc") String direction) {
        OrganizationContext ctx = context.require();
        String property = SORTABLE.getOrDefault(sort, "name");
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100),
                Sort.by(dir, property).and(Sort.by("name")));
        return PageResponse.of(service.list(ctx, new Filters(search, status, type, environment, groupId, enabled), pageable)
                .map(ResourceMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable UUID id) {
        return ResourceMapper.toResponse(service.get(context.require(), id));
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest body) {
        ResourceResponse created = ResourceMapper.toResponse(service.create(context.require(), body));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResourceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateResourceRequest body) {
        return ResourceMapper.toResponse(service.update(context.require(), id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(context.require(), id);
        return ResponseEntity.noContent().build();
    }

    /** Metadados estáticos para formulários do frontend (rótulos em português). */
    @GetMapping("/options")
    public Map<String, Object> options() {
        context.require();
        return Map.of(
                "types", List.of(ResourceType.values()).stream()
                        .map(t -> Map.of("value", t.name(), "label", t.getLabel(), "httpCheck", t.isHttpCheck())).toList(),
                "environments", List.of(Environment.values()).stream()
                        .map(e -> Map.of("value", e.name(), "label", e.getLabel())).toList(),
                "statuses", List.of(ResourceStatus.values()).stream()
                        .map(s -> Map.of("value", s.name(), "label", s.getLabel())).toList(),
                "intervals", List.of(30, 60, 300, 900));
    }
}
