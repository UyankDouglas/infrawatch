package com.infrawatch.reports;

import com.infrawatch.reports.ReportDtos.AvailabilityReport;
import com.infrawatch.reports.ReportDtos.ExportRequest;
import com.infrawatch.reports.ReportDtos.ExportResponse;
import com.infrawatch.reports.ReportDtos.IncidentsReport;
import com.infrawatch.reports.ReportDtos.ResourceReport;
import com.infrawatch.security.CurrentContextService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;
    private final ExportService exports;
    private final CurrentContextService context;

    public ReportController(ReportService reports, ExportService exports, CurrentContextService context) {
        this.reports = reports;
        this.exports = exports;
        this.context = context;
    }

    @GetMapping("/availability")
    public AvailabilityReport availability(@RequestParam(defaultValue = "7d") String period,
                                           @RequestParam(defaultValue = "excluded") String maintenance,
                                           @RequestParam(required = false) UUID groupId) {
        return reports.availability(context.require(), period, maintenance, groupId);
    }

    @GetMapping("/incidents")
    public IncidentsReport incidents(@RequestParam(defaultValue = "7d") String period,
                                     @RequestParam(required = false) UUID resourceId) {
        return reports.incidentsReport(context.require(), period, resourceId);
    }

    @GetMapping("/resources/{id}")
    public ResourceReport resource(@PathVariable UUID id, @RequestParam(defaultValue = "7d") String period,
                                   @RequestParam(defaultValue = "excluded") String maintenance) {
        return reports.resourceReport(context.require(), id, period, maintenance);
    }

    @PostMapping("/exports")
    public ResponseEntity<ExportResponse> export(@RequestBody ExportRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exports.create(context.require(), body));
    }

    @GetMapping("/exports")
    public List<ExportResponse> history() {
        return exports.history(context.require());
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        ReportExport e = exports.download(context.require(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(e.getFileName()).build().toString())
                .body(e.getContent());
    }
}
