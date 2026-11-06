package com.infrawatch.reports;

import com.infrawatch.audit.AuditService;
import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.common.ApiException;
import com.infrawatch.monitoring.HealthCheckExecution;
import com.infrawatch.monitoring.HealthCheckExecutionRepository;
import com.infrawatch.monitoring.Periods;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.reports.ReportDtos.AvailabilityReport;
import com.infrawatch.reports.ReportDtos.ExportRequest;
import com.infrawatch.reports.ReportDtos.ExportResponse;
import com.infrawatch.reports.ReportDtos.IncidentRow;
import com.infrawatch.reports.ReportDtos.IncidentsReport;
import com.infrawatch.reports.ReportDtos.ResourceAvailabilityRow;
import com.infrawatch.security.OrganizationContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gera exportações CSV/XLSX a partir dos relatórios e mantém o histórico com os mesmos filtros e permissões. */
@Service
public class ExportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Sao_Paulo"));
    private static final int MAX_CHECK_ROWS = 20_000;

    private final ReportService reports;
    private final ReportExportRepository exports;
    private final HealthCheckExecutionRepository executions;
    private final UserRepository users;
    private final AuditService audit;

    public ExportService(ReportService reports, ReportExportRepository exports, HealthCheckExecutionRepository executions,
                         UserRepository users, AuditService audit) {
        this.reports = reports;
        this.exports = exports;
        this.executions = executions;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public ExportResponse create(OrganizationContext ctx, ExportRequest req) {
        ctx.require(MemberRole.VIEWER);
        String type = req.type() == null ? "AVAILABILITY" : req.type().toUpperCase(Locale.ROOT);
        String format = req.format() == null ? "CSV" : req.format().toUpperCase(Locale.ROOT);
        if (!List.of("AVAILABILITY", "INCIDENTS", "CHECKS").contains(type)) throw ApiException.badRequest("export_type", "Tipo de exportação inválido.");
        if (!List.of("CSV", "XLSX").contains(format)) throw ApiException.badRequest("export_format", "Formato inválido: use CSV ou XLSX.");
        String period = req.period() == null ? "7d" : req.period();
        Periods.parse(period);
        String maintenance = "counted".equalsIgnoreCase(req.maintenance()) ? "counted" : "excluded";

        Table table = switch (type) {
            case "AVAILABILITY" -> availabilityTable(reports.availability(ctx, period, maintenance, req.groupId()));
            case "INCIDENTS" -> incidentsTable(reports.incidentsReport(ctx, period, null));
            default -> checksTable(ctx, period);
        };
        byte[] bytes = format.equals("CSV") ? csv(table) : xlsx(table, typeLabel(type));
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.now());
        String fileName = "infrawatch-" + type.toLowerCase(Locale.ROOT) + "-" + period + "-" + stamp + "." + format.toLowerCase(Locale.ROOT);
        String contentType = format.equals("CSV") ? "text/csv; charset=utf-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        ReportExport export = exports.save(new ReportExport(ctx.organizationId(), ctx.userId(), type, format, period, maintenance,
                req.groupId() == null ? null : "groupId=" + req.groupId(), fileName, contentType, bytes, table.rows.size()));
        audit.record(ctx.organizationId(), ctx.userId(), "report.exported", "report_export", export.getId(),
                "Exportação " + typeLabel(type) + " (" + format + ", " + period + ") com " + table.rows.size() + " linha(s)");
        return toResponse(export);
    }

    @Transactional(readOnly = true)
    public List<ExportResponse> history(OrganizationContext ctx) {
        return exports.findTop50ByOrganizationIdOrderByCreatedAtDesc(ctx.organizationId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReportExport download(OrganizationContext ctx, UUID id) {
        ReportExport e = exports.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Exportação não encontrada."));
        e.getContent(); // inicializa o LOB dentro da transação
        return e;
    }

    // ------------------------------------------------------------------ tabelas

    record Table(List<String> headers, List<List<Object>> rows) {}

    private Table availabilityTable(AvailabilityReport r) {
        List<String> h = List.of("Recurso", "Tipo", "Ambiente", "Grupo", "Status atual", "Disponibilidade (%)", "Tempo observado (s)",
                "Tempo disponível (s)", "Tempo indisponível (s)", "Tempo em manutenção (s)", "Verificações", "Falhas",
                "Latência média (ms)", "Latência máxima (ms)", "Latência p95 (ms)", "Incidentes", "MTTR (s)", "MTBF (s)", "Observação MTBF",
                "Período", "Manutenções");
        List<List<Object>> rows = new ArrayList<>();
        for (ResourceAvailabilityRow x : r.rows()) {
            rows.add(List.of(x.name(), x.typeLabel(), x.environmentLabel(), n(x.groupName()), x.currentStatusLabel(), n(x.availabilityPercent()),
                    x.observedSeconds(), x.availableSeconds(), x.unavailableSeconds(), x.maintenanceSeconds(), x.checks(), x.failedChecks(),
                    n(x.avgResponseMs()), n(x.maxResponseMs()), n(x.p95ResponseMs()), x.incidents(), n(x.mttrSeconds()), n(x.mtbfSeconds()),
                    n(x.mtbfNote()), r.period(), r.maintenanceMode().equals("excluded") ? "excluídas" : "contadas como indisponibilidade"));
        }
        return new Table(h, rows);
    }

    private Table incidentsTable(IncidentsReport r) {
        List<String> h = List.of("Número", "Título", "Severidade", "Status", "Origem", "Recurso", "Responsável", "Aberto em",
                "Reconhecido em", "Resolvido em", "Duração (s)", "Tempo até reconhecer (s)", "Causa");
        List<List<Object>> rows = new ArrayList<>();
        for (IncidentRow i : r.rows()) {
            rows.add(List.of(i.number(), i.title(), i.severityLabel(), i.statusLabel(), i.originLabel(), n(i.resourceName()), n(i.assignee()),
                    ts(i.openedAt()), ts(i.acknowledgedAt()), ts(i.resolvedAt()), n(i.durationSeconds()), n(i.timeToAcknowledgeSeconds()), n(i.cause())));
        }
        return new Table(h, rows);
    }

    private Table checksTable(OrganizationContext ctx, String period) {
        Duration window = Periods.parse(period);
        Instant to = Instant.now();
        Instant from = to.minus(window);
        List<HealthCheckExecution> list = executions.findRecentForOrganization(ctx.organizationId(), from, to, MAX_CHECK_ROWS);
        List<String> h = List.of("Recurso", "Início", "Sucesso", "HTTP", "Tempo de resposta (ms)", "Erro", "Mensagem", "Endereço", "Execução");
        List<List<Object>> rows = new ArrayList<>();
        var names = executions.resourceNames(ctx.organizationId());
        for (HealthCheckExecution e : list) {
            rows.add(List.of(names.getOrDefault(e.getResourceId(), "Recurso removido"), ts(e.getStartedAt()), e.isSuccess() ? "sim" : "não",
                    n(e.getHttpStatus()), n(e.getResponseTimeMs()), n(e.getErrorCode()), n(e.getErrorMessage()), n(e.getResolvedAddress()),
                    e.getRunId().toString()));
        }
        return new Table(h, rows);
    }

    // ------------------------------------------------------------------ formatos

    static byte[] csv(Table t) {
        StringBuilder sb = new StringBuilder("﻿"); // BOM: Excel abre UTF-8 corretamente
        sb.append(String.join(";", t.headers)).append("\r\n");
        for (List<Object> row : t.rows) {
            List<String> cells = new ArrayList<>();
            for (Object v : row) cells.add(csvCell(v));
            sb.append(String.join(";", cells)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvCell(Object v) {
        String s = v == null ? "" : v instanceof Double d ? String.format(Locale.ROOT, "%.2f", d).replace('.', ',') : String.valueOf(v);
        // Neutraliza fórmulas (CSV injection) e escapa aspas/separador
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+([.,]\\d+)?")) s = "'" + s;
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    static byte[] xlsx(Table t, String sheetName) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            Row hr = sheet.createRow(0);
            for (int c = 0; c < t.headers.size(); c++) {
                Cell cell = hr.createCell(c);
                cell.setCellValue(t.headers.get(c));
                cell.setCellStyle(header);
            }
            int rIdx = 1;
            for (List<Object> row : t.rows) {
                Row r = sheet.createRow(rIdx++);
                for (int c = 0; c < row.size(); c++) {
                    Object v = row.get(c);
                    Cell cell = r.createCell(c);
                    if (v instanceof Number num) cell.setCellValue(num.doubleValue());
                    else cell.setCellValue(v == null ? "" : String.valueOf(v));
                }
            }
            for (int c = 0; c < t.headers.size(); c++) sheet.setColumnWidth(c, Math.min(60, Math.max(12, t.headers.get(c).length() + 4)) * 256);
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX", e);
        }
    }

    private static Object n(Object v) { return v == null ? "" : v; }
    private static String ts(Instant i) { return i == null ? "" : TS.format(i); }

    static String typeLabel(String type) {
        return switch (type) {
            case "AVAILABILITY" -> "Disponibilidade";
            case "INCIDENTS" -> "Incidentes";
            case "CHECKS" -> "Verificações";
            default -> type;
        };
    }

    private ExportResponse toResponse(ReportExport e) {
        String by = e.getUserId() == null ? null : users.findById(e.getUserId()).map(User::getFullName).orElse("Usuário removido");
        return new ExportResponse(e.getId(), e.getReportType(), typeLabel(e.getReportType()), e.getFormat(), e.getPeriod(),
                e.getMaintenanceMode(), e.getFileName(), e.getContentType(), e.getSizeBytes(), e.getRowCount(), by, e.getCreatedAt());
    }
}
