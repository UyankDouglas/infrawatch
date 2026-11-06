package com.infrawatch.notifications;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndOrganizationIdOrderByCreatedAtDesc(UUID userId, UUID organizationId, Pageable pageable);

    Page<Notification> findByUserIdAndOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, UUID organizationId, Pageable pageable);

    long countByUserIdAndOrganizationIdAndReadAtIsNull(UUID userId, UUID organizationId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.userId = :userId and n.organizationId = :orgId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("orgId") UUID organizationId, @Param("at") Instant at);

    List<Notification> findTop5ByUserIdAndOrganizationIdOrderByCreatedAtDesc(UUID userId, UUID organizationId);
}
