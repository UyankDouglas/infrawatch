package com.infrawatch.integrations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    boolean existsByChannelIdAndDedupeKey(UUID channelId, String dedupeKey);

    Page<NotificationDelivery> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<NotificationDelivery> findByOrganizationIdAndChannelIdOrderByCreatedAtDesc(UUID organizationId, UUID channelId, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, NotificationDelivery.Status status);

    /** Reivindica entregas pendentes vencidas (bloqueio por linha para múltiplas instâncias). */
    @Query(value = """
            SELECT * FROM notification_deliveries
             WHERE status = 'PENDING' AND next_attempt_at <= :now
             ORDER BY next_attempt_at
             LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationDelivery> claimDue(@Param("now") Instant now, @Param("limit") int limit);
}
