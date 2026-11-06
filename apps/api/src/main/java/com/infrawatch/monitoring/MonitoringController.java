package com.infrawatch.monitoring;

import com.infrawatch.common.ApiException;
import com.infrawatch.common.PageResponse;
import com.infrawatch.monitoring.CheckResultApplier.Applied;
import com.infrawatch.monitoring.HealthCheckExecutionRepository.BucketStats;
import com.infrawatch.monitoring.MonitoringDtos.ExecutionResponse;
import com.infrawatch.monitoring.MonitoringDtos.HistoryResponse;
import com.infrawatch.monitoring.MonitoringDtos.LatencyPoint;
import com.infrawatch.monitoring.MonitoringDtos.ResourceMetricsResponse;
import com.infrawatch.monitoring.MonitoringDtos.RunNowResponse;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import com.infrawatch.security.CurrentContextService;
import com.infrawatch.security.OrganizationContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resources/{id}")
public class MonitoringController {

    private final CurrentContextService context;
    private final MonitoredResourceRepository resources;
    private final HealthCheckExecutionRepository executions;
    private final ResourceStatusHistoryRepository history;
    private final CheckRunner runner;

    public MonitoringController(CurrentContextService context, MonitoredResourceRepository resources,
                                HealthCheckExecutionRepository executions, ResourceStatusHistoryRepository history,
                                CheckRunner runner) {
        this.context = context;
        this.resources = resources;
        this.executions = executions;
        this.history = history;
        this.runner = runner;
    }

    @GetMapping("/checks")
    public PageResponse<ExecutionResponse> checks(@PathVariable UUID id,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        OrganizationContext ctx = context.require();
        requireResource(id, ctx);
        return PageResponse.of(executions.findByResourceIdAndOrganizationIdOrderByStartedAtDesc(id,
                ctx.organizationId(), PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200)))
                .map(ExecutionResponse::of));
    }

    @GetMapping("/history")
    public PageResponse<HistoryResponse> history(@PathVariable UUID id,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        OrganizationContext ctx = context.require();
        requireResource(id, ctx);
        return PageResponse.of(history.findByResourceIdAndOrganizationIdOrderByChangedAtDesc(id,
                ctx.organizationId(), PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200)))
                .map(HistoryResponse::of));
    }

    @GetMapping("/metrics")
    public ResourceMetricsResponse metrics(@PathVariable UUID id,
                                           @RequestParam(defaultValue = "24h") String period,
                                           @RequestParam(defaultValue = "excluded") String maintenance) {
        AvailabilityCalculator.MaintenanceMode mode = "counted".equalsIgnoreCase(maintenance)
                ? AvailabilityCalculator.MaintenanceMode.COUNTED_AS_DOWNTIME : AvailabilityCalculator.MaintenanceMode.EXCLUDED;
        OrganizationContext ctx = context.require();
        MonitoredResource resource = requireResource(id, ctx);
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);
        int bucketSeconds = Periods.bucketSeconds(window);

        List<BucketStats> stats = executions.bucketStatsForResource(id, ctx.organizationId(), from, to, bucketSeconds);
        List<LatencyPoint> points = stats.stream().map(s -> new LatencyPoint(s.getBucket(), s.getTotal(),
                s.getSuccesses(), s.getAvgResponseMs() == null ? null : Math.round(s.getAvgResponseMs() * 10) / 10.0,
                s.getMaxResponseMs(), s.getTotal() == 0 ? null : Math.round(s.getSuccesses() * 10000.0 / s.getTotal()) / 100.0))
                .toList();

        List<ResourceStatusHistory> transitions = history
                .findByResourceIdAndChangedAtGreaterThanEqualOrderByChangedAtAsc(id, from);
        // Antes da criação o recurso não existia: a janela observada começa, no máximo, na criação.
        if (resource.getCreatedAt().isAfter(from)) {
            from = resource.getCreatedAt();
        }
        ResourceStatus statusAtStart = transitions.isEmpty()
                ? resource.getCurrentStatus() : transitions.getFirst().getFromStatus();
        AvailabilityCalculator.Result availability = AvailabilityCalculator.compute(from, to, statusAtStart, transitions, mode);

        return new ResourceMetricsResponse(id, from, to, bucketSeconds, points, availability.percent(),
                availability.observedSeconds(), availability.availableSeconds(),
                mode == AvailabilityCalculator.MaintenanceMode.EXCLUDED
                        ? "Disponibilidade baseada em tempo com estado conhecido; períodos sem dados/aguardando e em manutenção são EXCLUÍDOS do tempo observado."
                        : "Disponibilidade baseada em tempo com estado conhecido; períodos em manutenção são CONTADOS como indisponibilidade.");
    }

    @PostMapping("/checks/run")
    public RunNowResponse runNow(@PathVariable UUID id) {
        OrganizationContext ctx = context.require();
        ctx.require(MemberRole.OPERATOR);
        MonitoredResource resource = requireResource(id, ctx);
        if (!resource.getType().isHttpCheck()) {
            throw ApiException.badRequest("check_not_supported",
                    "Este tipo de recurso depende de agente (Fase 2) e não possui verificação HTTP.");
        }
        Optional<Applied> applied = runner.run(id, 5_000);
        if (applied.isEmpty()) {
            return new RunNowResponse(false, null, resource.getCurrentStatus(), resource.getCurrentStatus(), false,
                    "Não foi possível executar agora (capacidade esgotada). Tente novamente em instantes.");
        }
        Applied a = applied.get();
        return new RunNowResponse(true, ExecutionResponse.of(a.execution()), a.previous(), a.current(),
                a.transitioned(), a.transitioned() ? "Estado alterado para " + a.current().getLabel() + "."
                        : "Verificação concluída sem mudança de estado.");
    }

    private MonitoredResource requireResource(UUID id, OrganizationContext ctx) {
        return resources.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Recurso não encontrado."));
    }
}
