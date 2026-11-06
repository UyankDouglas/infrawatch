package com.infrawatch.maintenance;

import com.infrawatch.maintenance.MaintenanceDtos.CreateRequest;
import com.infrawatch.maintenance.MaintenanceDtos.PatchRequest;
import com.infrawatch.maintenance.MaintenanceDtos.WindowResponse;
import com.infrawatch.security.CurrentContextService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MaintenanceController {

    private final MaintenanceService service;
    private final CurrentContextService context;

    public MaintenanceController(MaintenanceService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping("/maintenances")
    public List<WindowResponse> list(@RequestParam(defaultValue = "false") boolean upcomingOnly) {
        return upcomingOnly ? service.upcoming(context.require()) : service.list(context.require());
    }

    @GetMapping("/maintenances/{id}")
    public WindowResponse get(@PathVariable UUID id) {
        return service.get(context.require(), id);
    }

    @PostMapping("/maintenances")
    public ResponseEntity<WindowResponse> create(@Valid @RequestBody CreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(context.require(), body));
    }

    @PatchMapping("/maintenances/{id}")
    public WindowResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchRequest body) {
        return service.patch(context.require(), id, body);
    }

    @PostMapping("/maintenances/{id}/finish")
    public WindowResponse finish(@PathVariable UUID id) {
        return service.finishEarly(context.require(), id);
    }

    @GetMapping("/resources/{id}/maintenances")
    public List<WindowResponse> forResource(@PathVariable UUID id) {
        return service.forResource(context.require(), id);
    }
}
