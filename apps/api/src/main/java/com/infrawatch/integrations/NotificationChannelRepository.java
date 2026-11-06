package com.infrawatch.integrations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, UUID> {
    List<NotificationChannel> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
    List<NotificationChannel> findByOrganizationIdAndEnabledTrue(UUID organizationId);
    Optional<NotificationChannel> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
