package com.pulserelay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RoutingPolicy entity for Phase 07: Configurable Notification Routing Policies.
 * Defines how notifications for specific event types should be routed across channels.
 */
@Entity
@Table(name = "routing_policies", indexes = {
    @Index(name = "idx_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_tenant_event_type", columnList = "tenant_id,event_type"),
    @Index(name = "idx_enabled", columnList = "enabled")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    /**
     * Tenant relationship - ensures tenant isolation
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_routing_policy_tenant"))
    private Tenant tenant;

    /**
     * Event type this policy applies to (e.g., ORDER_CONFIRMED, PAYMENT_FAILED, DELIVERY_UPDATE)
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Whether this policy is active
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /**
     * Primary notification channels (comma-separated: EMAIL,SMS,PUSH,IN_APP)
     */
    @Column(name = "primary_channels", nullable = false, length = 255)
    private String primaryChannels;

    /**
     * Fallback channels if primary channels fail (comma-separated)
     */
    @Column(name = "fallback_channels", nullable = true, length = 255)
    private String fallbackChannels;

    /**
     * Priority level for this notification type (CRITICAL, HIGH, NORMAL, LOW)
     */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    /**
     * Timestamp when this policy was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when this policy was last updated
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
