package com.pulserelay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * NotificationTemplate Entity - Phase 08
 * Stores reusable, tenant-specific notification templates with variables.
 * Templates contain subject and body with placeholders like {{variableName}}.
 */
@Entity
@Table(name = "notification_templates", indexes = {
        @Index(name = "idx_template_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_template_tenant_event", columnList = "tenant_id, event_type"),
        @Index(name = "idx_template_tenant_channel", columnList = "tenant_id, channel")
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class NotificationTemplate {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String channel;  // EMAIL, SMS, PUSH, IN_APP

    @Column(length = 500)
    private String subject;  // Can be null for SMS/PUSH

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.enabled == null) {
            this.enabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
