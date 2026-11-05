package com.pulserelay.repository;

import com.pulserelay.entity.Notification;
import com.pulserelay.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Notification entity.
 * Handles database operations for notifications.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find an existing notification by tenant and idempotency key.
     * Used to detect and handle duplicate requests.
     * 
     * Uniqueness is enforced at the database level:
     * UNIQUE INDEX idx_tenant_idempotency ON notifications(tenant_id, idempotency_key)
     * 
     * @param tenant The tenant that owns the notification
     * @param idempotencyKey The idempotency key from the request
     * @return Optional containing the existing notification if found
     */
    Optional<Notification> findByTenantAndIdempotencyKey(Tenant tenant, String idempotencyKey);
}
