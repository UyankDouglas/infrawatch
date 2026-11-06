package com.infrawatch.reports;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Arquivo exportado (CSV/XLSX) com metadados para o histórico de exportações. */
@Entity
@Table(name = "report_exports")
public class ReportExport {

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "report_type", nullable = false, length = 30) private String reportType;
    @Column(nullable = false, length = 10) private String format;
    @Column(nullable = false, length = 10) private String period;
    @Column(name = "maintenance_mode", nullable = false, length = 20) private String maintenanceMode;
    @Column(length = 500) private String filters;
    @Column(name = "file_name", nullable = false, length = 200) private String fileName;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "row_count", nullable = false) private int rowCount;
    @Basic(fetch = FetchType.LAZY) @Column(nullable = false) private byte[] content;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ReportExport() {}

    public ReportExport(UUID organizationId, UUID userId, String reportType, String format, String period, String maintenanceMode,
                        String filters, String fileName, String contentType, byte[] content, int rowCount) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.userId = userId;
        this.reportType = reportType;
        this.format = format;
        this.period = period;
        this.maintenanceMode = maintenanceMode;
        this.filters = filters;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.rowCount = rowCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getUserId() { return userId; }
    public String getReportType() { return reportType; }
    public String getFormat() { return format; }
    public String getPeriod() { return period; }
    public String getMaintenanceMode() { return maintenanceMode; }
    public String getFilters() { return filters; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getRowCount() { return rowCount; }
    public byte[] getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
