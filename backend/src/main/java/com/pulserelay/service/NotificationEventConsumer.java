package com.pulserelay.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for processing notification events.
 * 
 * Consumes events from the notification-events topic (published by OutboxPublisher in Phase 05).
 * Implements consumer-side deduplication to ensure idempotent processing.
 * 
 * Message Flow:
 * 1. Consumer receives message from Kafka
 * 2. Validates event structure and headers
 * 3. Extracts event metadata (ID, type, aggregate ID)
 * 4. Invokes NotificationProcessingService for business logic
 * 5. Acknowledges message only after successful processing
 * 6. On error: Logs and does NOT acknowledge (Kafka will redeliver)
 * 
 * Idempotency Guarantees:
 * - Same event ID is only processed once (checked in ProcessedEventRepository)
 * - Duplicate Kafka messages are safely ignored
 * - Database constraint ensures uniqueness of (event_id) in processed_events table
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationProcessingService processingService;

    /**
     * Consume a notification event from Kafka.
     * 
     * Topic: notification-events (configured in application.yml)
     * Group ID: pulserelay-consumer-group
     * 
     * Headers (from outbox event):
     * - X-Event-ID: Unique event identifier (for deduplication)
     * - X-Event-Type: Type of event (e.g., "notification.created")
     * 
     * Payload: Event JSON containing:
     * - eventId: Unique event ID
     * - eventType: Type of event
     * - notificationId: Notification aggregate ID
     * - tenantId: Tenant that owns the notification
     * - occurredAt: When the notification was created
     * - payload: Notification details (channels, recipient, data)
     * 
     * @param payload The complete event payload as a map
     * @param eventIdHeader X-Event-ID header value
     * @param eventTypeHeader X-Event-Type header value
     * @param partition Kafka partition this message came from
     * @param offset Kafka offset of this message
     * @param acknowledgment Manual acknowledgment handle (called after success)
     */
    @KafkaListener(
        topics = "${pulserelay.kafka.topic.notifications:notification-events}",
        groupId = "${spring.kafka.consumer.group-id:pulserelay-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeNotificationEvent(
            @Payload Map<String, Object> payload,
            @Header("X-Event-ID") String eventIdHeader,
            @Header("X-Event-Type") String eventTypeHeader,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset,
            Acknowledgment acknowledgment) {

        try {
            log.info("Received Kafka message: partition={}, offset={}, eventId={}, eventType={}",
                partition, offset, eventIdHeader, eventTypeHeader);

            // Extract and validate required fields
            UUID eventId = parseEventId(eventIdHeader);
            String eventType = eventTypeHeader;

            // Extract event metadata from payload
            Map<String, Object> eventMetadata = extractEventMetadata(payload);
            UUID notificationId = (UUID) eventMetadata.get("notificationId");
            UUID tenantId = (UUID) eventMetadata.get("tenantId");

            log.debug("Event metadata: notificationId={}, tenantId={}, eventType={}",
                notificationId, tenantId, eventType);

            // Process the notification event
            processingService.processNotificationEvent(
                eventId,
                notificationId,
                eventType,
                payload,
                partition,
                offset
            );

            // Only acknowledge after successful processing
            acknowledgment.acknowledge();
            log.info("Message acknowledged: eventId={}, notificationId={}, partition={}, offset={}",
                eventId, notificationId, partition, offset);

        } catch (IllegalArgumentException validationException) {
            // Notification not found or validation failed - don't retry
            log.error("Validation error processing message (partition={}, offset={}): {}. " +
                    "Message will be skipped without retry.",
                partition, offset, validationException.getMessage());
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite loop

        } catch (Exception exception) {
            // Unexpected error - do NOT acknowledge
            // Kafka will redeliver this message on the next poll
            log.error("Error processing Kafka message (partition={}, offset={}). " +
                    "Message will be redelivered by Kafka for retry.",
                partition, offset, exception);
            // No acknowledgment = message stays in Kafka for retry
        }
    }

    /**
     * Parse and validate event ID from header.
     * 
     * @param eventIdHeader String representation of UUID
     * @return Parsed UUID
     * @throws IllegalArgumentException If header is invalid
     */
    private UUID parseEventId(String eventIdHeader) {
        if (eventIdHeader == null || eventIdHeader.isEmpty()) {
            throw new IllegalArgumentException("X-Event-ID header is missing or empty");
        }
        try {
            return UUID.fromString(eventIdHeader);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid event ID format: " + eventIdHeader);
        }
    }

    /**
     * Extract event metadata from payload.
     * 
     * Expected structure:
     * {
     *   "eventId": "...",
     *   "eventType": "notification.created",
     *   "notificationId": "...",
     *   "tenantId": "...",
     *   "occurredAt": "2026-11-13T18:00:00...",
     *   "payload": { ... }
     * }
     * 
     * @param payload The event payload
     * @return Map containing extracted metadata
     * @throws IllegalArgumentException If required fields are missing
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractEventMetadata(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Event payload is empty");
        }

        // Extract notification ID
        Object notificationIdObj = payload.get("notificationId");
        if (notificationIdObj == null) {
            throw new IllegalArgumentException("notificationId is missing from payload");
        }
        UUID notificationId = parseUUID(notificationIdObj, "notificationId");

        // Extract tenant ID
        Object tenantIdObj = payload.get("tenantId");
        if (tenantIdObj == null) {
            throw new IllegalArgumentException("tenantId is missing from payload");
        }
        UUID tenantId = parseUUID(tenantIdObj, "tenantId");

        return Map.of(
            "notificationId", notificationId,
            "tenantId", tenantId
        );
    }

    /**
     * Parse a UUID from payload object (may come as String or UUID).
     * 
     * @param obj Object that should be a UUID
     * @param fieldName Field name for error messages
     * @return Parsed UUID
     * @throws IllegalArgumentException If object cannot be parsed as UUID
     */
    private UUID parseUUID(Object obj, String fieldName) {
        if (obj instanceof UUID) {
            return (UUID) obj;
        }
        if (obj instanceof String) {
            try {
                return UUID.fromString((String) obj);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid UUID format for " + fieldName + ": " + obj);
            }
        }
        throw new IllegalArgumentException("Invalid type for " + fieldName + ": " + obj.getClass());
    }
}
