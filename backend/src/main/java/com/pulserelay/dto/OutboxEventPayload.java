package com.pulserelay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payload structure for outbox events published to Kafka.
 * Provides event versioning and a consistent structure for all event types.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventPayload {

    /**
     * Unique event identifier (outbox event ID).
     * Used for idempotency in downstream consumers.
     */
    private UUID eventId;

    /**
     * Event type identifier (e.g., "notification.created").
     * Used for routing and filtering.
     */
    private String eventType;

    /**
     * Aggregate root ID (notification ID).
     * The business entity this event describes.
     */
    private UUID notificationId;

    /**
     * Tenant ID for multi-tenant isolation.
     * Required to scope events to tenants in downstream services.
     */
    private UUID tenantId;

    /**
     * Timestamp when the event occurred.
     * The business time (when the notification was created).
     */
    private LocalDateTime occurredAt;

    /**
     * Event-specific payload.
     * Contains notification details (eventType, recipient, channels, priority, data).
     */
    private Map<String, Object> payload;

    /**
     * Event schema version.
     * Used for backward compatibility when the event structure evolves.
     */
    @Builder.Default
    private Integer version = 1;

    /**
     * Create an OutboxEventPayload from notification details.
     * 
     * @param outboxEventId The outbox event ID (for event idempotency)
     * @param notificationId The notification ID (aggregate root)
     * @param tenantId The tenant that created the notification
     * @param notificationCreatedAt When the notification was created
     * @param notificationPayload The notification details
     * @return OutboxEventPayload ready for Kafka publication
     */
    public static OutboxEventPayload forNotificationCreated(
            UUID outboxEventId,
            UUID notificationId,
            UUID tenantId,
            LocalDateTime notificationCreatedAt,
            Map<String, Object> notificationPayload) {

        return OutboxEventPayload.builder()
            .eventId(outboxEventId)
            .eventType("notification.created")
            .notificationId(notificationId)
            .tenantId(tenantId)
            .occurredAt(notificationCreatedAt)
            .payload(notificationPayload)
            .version(1)
            .build();
    }
}
