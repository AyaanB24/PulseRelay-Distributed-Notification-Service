package com.pulserelay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Notification entity representing a notification request submitted by a tenant.
 * Each notification belongs to exactly one tenant and must be processed independently.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_tenant_idempotency", columnList = "tenant_id, idempotency_key", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    /**
     * Tenant that owns this notification.
     * Every notification must belong to exactly one tenant.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Type of event (e.g., order_confirmation, payment_success, user_signup).
     * Allows tenants to categorize and filter notifications.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Email address of the notification recipient.
     * Optional — notification may use phone or userId instead.
     */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    /**
     * Phone number of the notification recipient.
     * Optional — notification may use email or userId instead.
     */
    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    /**
     * User ID of the notification recipient (tenant's internal user ID).
     * Optional — notification may use email or phone instead.
     */
    @Column(name = "recipient_user_id", length = 255)
    private String recipientUserId;

    /**
     * Comma-separated list of requested channels.
     * Examples: EMAIL, SMS, PUSH, IN_APP
     * Allows specifying multiple delivery channels for a single notification.
     */
    @Column(name = "requested_channels", nullable = false, length = 255)
    private String requestedChannels;

    /**
     * Priority level for notification delivery.
     * CRITICAL: Must be delivered immediately (e.g., account verification)
     * HIGH: Should be delivered within minutes (e.g., order confirmation)
     * NORMAL: Standard delivery (e.g., promotional content)
     * LOW: Best-effort delivery (e.g., digest emails)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private Priority priority;

    /**
     * JSON payload/data for the notification.
     * Contains template variables, content, and metadata.
     * Example: {"orderId": "12345", "amount": 99.99, "currency": "USD"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /**
     * Current status of the notification in the processing pipeline.
     * PENDING: Received and queued for processing
     * PROCESSING: Being sent to delivery provider(s)
     * SENT: Successfully delivered to at least one channel
     * FAILED: Failed to deliver through all channels
     * EXPIRED: Notification TTL exceeded
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    /**
     * Idempotency key for deduplication.
     * Tenant-scoped: Must be unique within a tenant (enforced at DB level).
     * Same tenant + same key = duplicate request, returns existing notification.
     * Different tenants may use the same key.
     */
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = NotificationStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum NotificationStatus {
        PENDING,      // Received and queued for processing
        PROCESSING,   // Being sent to delivery provider(s)
        SENT,         // Successfully delivered
        FAILED,       // Failed to deliver
        EXPIRED       // TTL exceeded
    }

    public enum Priority {
        CRITICAL,     // Immediate delivery required
        HIGH,         // Deliver within minutes
        NORMAL,       // Standard delivery
        LOW           // Best-effort delivery
    }
}
