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
 * Outbox Event entity implementing the Transactional Outbox Pattern with retry support.
 * 
 * The Outbox Pattern ensures reliable event publishing:
 * 1. Notification is saved to the database
 * 2. Corresponding outbox event is saved in the same transaction
 * 3. A separate publisher polls eligible events (PENDING or retryable FAILED) and publishes them to Kafka
 * 4. Published events are marked as PUBLISHED for audit trail
 * 5. Failed events are eligible for retry based on retry count and backoff time
 * 
 * This prevents data loss: If Kafka publish fails, the event remains in the outbox
 * and will be retried by the publisher after backoff delay. If the database transaction fails, 
 * neither the notification nor the outbox event are saved.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_aggregate_id", columnList = "aggregate_id"),
    @Index(name = "idx_status_created", columnList = "status, created_at"),
    @Index(name = "idx_retryable", columnList = "status, retry_count, last_attempted_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    /**
     * Type of event (e.g., "notification.created", "notification.sent").
     * Used for routing and filtering in downstream consumers.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Aggregate root ID (notification ID).
     * Links the outbox event to the notification that triggered it.
     */
    @Column(name = "aggregate_id", nullable = false, columnDefinition = "UUID")
    private UUID aggregateId;

    /**
     * Event payload as JSON.
     * Contains: eventId, eventType, notificationId, tenantId, occurredAt, payload
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    /**
     * Status of the event in the publishing pipeline.
     * PENDING: Created, waiting to be published to Kafka (never attempted)
     * PUBLISHED: Successfully published and acknowledged by broker
     * FAILED: Publication attempt failed (may be retried based on retry count and backoff)
     * TERMINAL_FAILED: Maximum retry attempts exhausted
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    /**
     * Number of retry attempts for publishing this event.
     * Incremented each time the publisher attempts to publish the event.
     * Used to determine if event should be retried or abandoned.
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    /**
     * Timestamp of the last attempted publication.
     * Used for exponential backoff: events are only retried if enough time has passed.
     * Null until first attempt.
     */
    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    /**
     * Timestamp when the event was created (same transaction as notification).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the event was successfully published to Kafka.
     * Null until PUBLISHED status.
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * Timestamp of the last update (status change, retry increment).
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = OutboxEventStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum OutboxEventStatus {
        PENDING,           // Waiting to be published (never attempted)
        PUBLISHED,         // Successfully published to Kafka
        FAILED,            // Publication failed (retryable)
        TERMINAL_FAILED    // Maximum retry attempts exhausted
    }
}
