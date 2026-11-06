package com.infrawatch.incidents;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentHistoryRepository extends JpaRepository<IncidentHistory, UUID> {
    List<IncidentHistory> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
