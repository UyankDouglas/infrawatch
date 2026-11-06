package com.infrawatch.reports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {
    List<ReportExport> findTop50ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    Optional<ReportExport> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
