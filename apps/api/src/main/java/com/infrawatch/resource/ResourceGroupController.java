package com.infrawatch.resource;

import com.infrawatch.resource.ResourceDtos.GroupRequest;
import com.infrawatch.resource.ResourceDtos.GroupResponse;
import com.infrawatch.security.CurrentContextService;
import com.infrawatch.security.OrganizationContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resource-groups")
public class ResourceGroupController {

    private final ResourceService service;
    private final CurrentContextService context;

    public ResourceGroupController(ResourceService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping
    public List<GroupResponse> list() {
        OrganizationContext ctx = context.require();
        return service.listGroups(ctx).stream().map(g -> toResponse(ctx, g)).toList();
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest body) {
        OrganizationContext ctx = context.require();
        ResourceGroup group = service.createGroup(ctx, body.name(), body.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(ctx, group));
    }

    @PutMapping("/{id}")
    public GroupResponse update(@PathVariable UUID id, @Valid @RequestBody GroupRequest body) {
        OrganizationContext ctx = context.require();
        return toResponse(ctx, service.updateGroup(ctx, id, body.name(), body.description()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteGroup(context.require(), id);
        return ResponseEntity.noContent().build();
    }

    private GroupResponse toResponse(OrganizationContext ctx, ResourceGroup g) {
        return new GroupResponse(g.getId(), g.getName(), g.getDescription(), g.getCreatedAt(),
                service.countInGroup(ctx, g.getId()));
    }
}
