package com.pulserelay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * DeliveryAttempt Entity - Phase 09
 * Records each delivery attempt with detailed status and provider information
 */
@Entity
@Table(name = "delivery_attempts", indexes = {
        @Index(name = "idx_attempt_notification_id", columnList = "notification_id"),
        @Index(name = "idx_attempt_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_attempt_status", columnList = "status"),
        @Index(name = "idx_attempt_channel", columnList = "channel")
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class DeliveryAttempt {

    @Id
    private UUID id;

    @Column(nullable = true)  // Nullable for test delivery attempts
    private UUID notificationId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String channel;  // EMAIL, SMS, PUSH, IN_APP

    @Column(nullable = false, length = 100)
    private String provider;  // Provider name (MockEmail, Smtp, etc.)

    @Column(length = 255)
    private String recipient;  // Email, phone, device token, etc.

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;  // ACCEPTED, DELIVERED, FAILED, PERMANENT_FAILURE

    @Column(length = 100)
    private String errorCode;  // Provider error code

    @Column(length = 500)
    private String errorMessage;  // Human-readable error

    @Column(length = 255)
    private String providerMessageId;  // Message ID from provider

    @Column(nullable = false)
    private Boolean isRetryable;

    @Column(nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;  // When to retry (null if not scheduled)

    public enum DeliveryStatus {
        ACCEPTED,           // Provider accepted the request
        DELIVERED,          // Message confirmed delivered
        FAILED,             // Delivery failed (potentially retryable)
        PERMANENT_FAILURE,  // Non-retryable failure
        RETRY_SCHEDULED     // Scheduled for retry
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.attemptedAt == null) {
            this.attemptedAt = Instant.now();
        }
    }
}
