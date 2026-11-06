package com.infrawatch.metrics;

import com.infrawatch.common.ApiException;
import com.infrawatch.metrics.MetricsDtos.IngestResponse;
import com.infrawatch.metrics.MetricsDtos.SamplePayload;
import com.infrawatch.metrics.AgentCredentialRepository;
import com.infrawatch.metrics.AgentRepository;
import com.infrawatch.metrics.MetricSampleRepository;
import com.infrawatch.monitoring.ResourceStatusHistory;
import com.infrawatch.monitoring.ResourceStatusHistoryRepository;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recebe amostras de agentes autenticados por token. Valida, normaliza percentuais, registra a amostra
 * e atualiza o estado do recurso (um servidor com agente reportando é considerado DISPONÍVEL).
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final AgentRepository agents;
    private final AgentCredentialRepository credentials;
    private final MetricSampleRepository samples;
    private final MonitoredResourceRepository resources;
    private final ResourceStatusHistoryRepository history;
    private final MetricsProperties properties;
    private final Counter accepted;
    private final Counter rejected;

    public IngestService(AgentRepository agents, AgentCredentialRepository credentials, MetricSampleRepository samples,
                         MonitoredResourceRepository resources, ResourceStatusHistoryRepository history,
                         MetricsProperties properties, MeterRegistry registry) {
        this.agents = agents;
        this.credentials = credentials;
        this.samples = samples;
        this.resources = resources;
        this.history = history;
        this.properties = properties;
        this.accepted = Counter.builder("infrawatch.engine.agent.samples.accepted").register(registry);
        this.rejected = Counter.builder("infrawatch.engine.agent.samples.rejected").register(registry);
    }

    public record AuthenticatedAgent(Agent agent, AgentCredential credential) {}

    @Transactional
    public AuthenticatedAgent authenticate(String authorizationHeader) {
        String token = bearer(authorizationHeader);
        String prefix = AgentTokens.prefixOf(token).orElseThrow(() -> unauthorized("Token de agente inválido."));
        AgentCredential credential = credentials.findByTokenPrefix(prefix)
                .orElseThrow(() -> unauthorized("Token de agente inválido."));
        if (!credential.isActive() || !AgentTokens.matches(token, credential.getTokenHash())) {
            throw unauthorized("Token de agente inválido ou revogado.");
        }
        Agent agent = agents.findById(credential.getAgentId()).orElseThrow(() -> unauthorized("Agente não encontrado."));
        if (agent.getStatus() != Agent.Status.ACTIVE) {
            throw unauthorized("Agente revogado.");
        }
        return new AuthenticatedAgent(agent, credential);
    }

    @Transactional
    public IngestResponse ingest(AuthenticatedAgent auth, SamplePayload payload) {
        // Recarrega dentro desta transação (a autenticação ocorreu em outra) para persistir as alterações.
        Agent agent = agents.findById(auth.agent().getId()).orElseThrow(() -> unauthorized("Agente não encontrado."));
        AgentCredential credential = credentials.findById(auth.credential().getId()).orElseThrow(() -> unauthorized("Credencial inválida."));
        Instant now = Instant.now();

        // Limite de frequência por agente: evita que um agente mal configurado inunde o servidor.
        if (agent.getLastSeenAt() != null
                && Duration.between(agent.getLastSeenAt(), now).compareTo(properties.minIngestSpacing()) < 0) {
            rejected.increment();
            throw ApiException.tooManyRequests("Amostras enviadas com frequência acima do permitido.");
        }

        Instant sampledAt = payload.sampledAt() == null ? now : payload.sampledAt();
        if (sampledAt.isAfter(now.plus(properties.maxClockSkew()))
                || sampledAt.isBefore(now.minus(properties.maxClockSkew().multipliedBy(12)))) {
            rejected.increment();
            throw ApiException.badRequest("sample_clock_skew",
                    "Horário da amostra fora da janela aceita; verifique o relógio do host do agente.");
        }

        MonitoredResource resource = resources.lockById(agent.getResourceId())
                .orElseThrow(() -> ApiException.notFound("Recurso do agente não encontrado."));

        List<String> ignored = new ArrayList<>();
        MetricSample sample = new MetricSample(resource.getId(), resource.getOrganizationId(), agent.getId(), sampledAt, now);

        sample.setCpu(scale(payload.cpuPercent()), scale(payload.loadAvg1m()));
        if (payload.cpuPercent() == null) ignored.add("cpu");

        if (payload.memory() != null && payload.memory().totalBytes() != null && payload.memory().totalBytes() > 0
                && payload.memory().usedBytes() != null) {
            long total = payload.memory().totalBytes();
            long used = Math.min(payload.memory().usedBytes(), total);
            sample.setMemory(total, used, percent(used, total));
        } else {
            ignored.add("memory");
        }

        if (payload.disk() != null && payload.disk().totalBytes() != null && payload.disk().totalBytes() > 0
                && payload.disk().usedBytes() != null) {
            long total = payload.disk().totalBytes();
            long used = Math.min(payload.disk().usedBytes(), total);
            sample.setDisk(payload.disk().path(), total, used, percent(used, total));
        } else {
            ignored.add("disk");
        }

        sample.setUptimeSeconds(payload.uptimeSeconds());
        if (payload.uptimeSeconds() == null) ignored.add("uptime");

        samples.save(sample);
        agent.touch(now, sampledAt, payload.hostname(), payload.agentVersion());
        credential.used(now);
        accepted.increment();

        // Estado do recurso: agente reportando => DISPONÍVEL (a partir de aguardando / sem dados / indisponível).
        ResourceStatus previous = resource.getCurrentStatus();
        resource.recordCheck(now, true, null, null, null);
        if (previous == ResourceStatus.PENDING || previous == ResourceStatus.UNKNOWN || previous == ResourceStatus.DOWN) {
            resource.applyStatus(ResourceStatus.UP, now);
            resource.resetCounters();
            history.save(new ResourceStatusHistory(resource.getId(), resource.getOrganizationId(), previous,
                    ResourceStatus.UP, "Agente \"" + agent.getName() + "\" passou a reportar métricas", null, now));
            log.info("Transição de estado: recurso={} {} -> UP (agente)", resource.getId(), previous);
        }
        return new IngestResponse(resource.getId(), now, agent.getIntervalSeconds(), ignored);
    }

    private static String bearer(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw unauthorized("Cabeçalho Authorization: Bearer <token> obrigatório.");
        }
        return header.substring(7).trim();
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "agent_unauthorized", message);
    }

    static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal percent(long used, long total) {
        if (total <= 0) return null;
        return BigDecimal.valueOf(used).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
    }
}
