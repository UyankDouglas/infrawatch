package com.infrawatch.alerts;

import com.infrawatch.alerts.AlertEnums.Metric;
import com.infrawatch.alerts.AlertEnums.Operator;
import com.infrawatch.alerts.AlertEnums.Severity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Regras iniciais criadas para toda nova organização. São editáveis/desativáveis pelo administrador e
 * cobrem apenas métricas que o InfraWatch coleta de fato.
 */
public final class DefaultAlertRules {
    private DefaultAlertRules() {}

    public static List<AlertRule> forOrganization(UUID organizationId, UUID createdBy) {
        AlertRule down = new AlertRule(organizationId, "Serviço indisponível", Metric.STATUS_DOWN, Severity.CRITICAL, createdBy);
        down.setDescription("Dispara quando um recurso é confirmado como Indisponível (após as falhas consecutivas configuradas).");

        AlertRule degraded = new AlertRule(organizationId, "Serviço degradado", Metric.STATUS_DEGRADED, Severity.WARNING, createdBy);
        degraded.setDescription("Dispara quando a latência mantém o recurso em Degradado.");

        AlertRule latency = new AlertRule(organizationId, "Latência alta", Metric.LATENCY, Severity.WARNING, createdBy);
        latency.setOperator(Operator.GT);
        latency.setThreshold(new BigDecimal("2000"));
        latency.setOccurrences(3);
        latency.setDescription("Tempo de resposta acima de 2 s em 3 verificações consecutivas.");

        AlertRule cpu = new AlertRule(organizationId, "CPU alta", Metric.CPU, Severity.HIGH, createdBy);
        cpu.setOperator(Operator.GT);
        cpu.setThreshold(new BigDecimal("90"));
        cpu.setOccurrences(3);
        cpu.setDurationSeconds(60);
        cpu.setDescription("Uso de CPU acima de 90% por pelo menos 3 amostras e 1 minuto.");

        AlertRule memory = new AlertRule(organizationId, "Memória alta", Metric.MEMORY, Severity.HIGH, createdBy);
        memory.setOperator(Operator.GT);
        memory.setThreshold(new BigDecimal("90"));
        memory.setOccurrences(3);
        memory.setDurationSeconds(60);
        memory.setDescription("Uso de memória acima de 90% por pelo menos 3 amostras e 1 minuto.");

        AlertRule disk = new AlertRule(organizationId, "Disco com pouco espaço", Metric.DISK, Severity.CRITICAL, createdBy);
        disk.setOperator(Operator.GT);
        disk.setThreshold(new BigDecimal("90"));
        disk.setOccurrences(1);
        disk.setDescription("Uso de disco acima de 90%.");

        AlertRule noData = new AlertRule(organizationId, "Agente sem dados", Metric.AGENT_NO_DATA, Severity.HIGH, createdBy);
        noData.setDescription("Dispara quando um servidor com agente deixa de reportar métricas.");

        return List.of(down, degraded, latency, cpu, memory, disk, noData);
    }
}
