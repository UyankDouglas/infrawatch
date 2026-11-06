package com.infrawatch.dashboard;

import com.infrawatch.dashboard.DashboardDtos.AvailabilitySeries;
import com.infrawatch.dashboard.DashboardDtos.LatencyResponse;
import com.infrawatch.dashboard.DashboardDtos.Overview;
import com.infrawatch.security.CurrentContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final CurrentContextService context;

    public DashboardController(DashboardService service, CurrentContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(defaultValue = "24h") String period) {
        return service.overview(context.require().organizationId(), period);
    }

    @GetMapping("/availability")
    public AvailabilitySeries availability(@RequestParam(defaultValue = "24h") String period) {
        return service.availability(context.require().organizationId(), period);
    }

    @GetMapping("/latency")
    public LatencyResponse latency(@RequestParam(defaultValue = "24h") String period,
                                   @RequestParam(defaultValue = "5") int limit) {
        return service.latency(context.require().organizationId(), period, limit);
    }
}
