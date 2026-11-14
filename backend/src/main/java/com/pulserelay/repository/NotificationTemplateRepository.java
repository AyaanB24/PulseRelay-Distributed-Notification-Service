package com.pulserelay.repository;

import com.pulserelay.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for NotificationTemplate - Phase 08
 * Provides tenant-isolated data access with queries scoped to tenant_id.
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    /**
     * Find all templates for a tenant
     */
    List<NotificationTemplate> findByTenantId(UUID tenantId);

    /**
     * Find templates for a tenant filtered by event type
     */
    List<NotificationTemplate> findByTenantIdAndEventType(UUID tenantId, String eventType);

    /**
     * Find templates for a tenant filtered by channel
     */
    List<NotificationTemplate> findByTenantIdAndChannel(UUID tenantId, String channel);

    /**
     * Find a specific template by tenant, event type, and channel
     * Used for duplicate prevention and template resolution
     */
    Optional<NotificationTemplate> findByTenantIdAndEventTypeAndChannel(
            UUID tenantId, String eventType, String channel);

    /**
     * Find template by ID and verify it belongs to the tenant (tenant isolation)
     */
    Optional<NotificationTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
}
