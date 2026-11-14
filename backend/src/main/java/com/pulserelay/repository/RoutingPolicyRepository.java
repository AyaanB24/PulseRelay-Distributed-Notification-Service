package com.pulserelay.repository;

import com.pulserelay.entity.RoutingPolicy;
import com.pulserelay.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RoutingPolicy entity.
 * Provides database queries for routing policy management.
 */
@Repository
public interface RoutingPolicyRepository extends JpaRepository<RoutingPolicy, UUID> {

    /**
     * Find a routing policy for a specific tenant and event type
     */
    Optional<RoutingPolicy> findByTenantIdAndEventType(UUID tenantId, String eventType);

    /**
     * Find all routing policies for a tenant
     */
    List<RoutingPolicy> findByTenantId(UUID tenantId);

    /**
     * Find all enabled routing policies for a tenant
     */
    List<RoutingPolicy> findByTenantIdAndEnabledTrue(UUID tenantId);

    /**
     * Check if a policy already exists for tenant and event type (for duplicate prevention)
     */
    @Query("SELECT COUNT(rp) > 0 FROM RoutingPolicy rp WHERE rp.tenant.id = :tenantId AND rp.eventType = :eventType AND rp.id != :policyId")
    boolean existsByTenantAndEventTypeExcluding(@Param("tenantId") UUID tenantId, @Param("eventType") String eventType, @Param("policyId") UUID policyId);

    /**
     * Check if a policy exists for tenant and event type
     */
    boolean existsByTenantIdAndEventType(UUID tenantId, String eventType);

    /**
     * Delete all policies for a tenant (cascade on tenant deletion)
     */
    void deleteByTenantId(UUID tenantId);
}
