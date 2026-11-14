package com.pulserelay.repository;

import com.pulserelay.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for DeliveryAttempt - Phase 09
 * Provides data access for delivery attempt records
 */
@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    /**
     * Find all delivery attempts for a notification
     */
    List<DeliveryAttempt> findByNotificationId(UUID notificationId);

    /**
     * Find delivery attempts by tenant and status
     */
    List<DeliveryAttempt> findByTenantIdAndStatus(UUID tenantId, DeliveryAttempt.DeliveryStatus status);

    /**
     * Find delivery attempts by channel
     */
    List<DeliveryAttempt> findByChannel(String channel);

    /**
     * Find failed delivery attempts that are retryable
     */
    List<DeliveryAttempt> findByStatusAndIsRetryable(DeliveryAttempt.DeliveryStatus status, Boolean isRetryable);
}
