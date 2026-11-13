package com.pulserelay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Processed Event entity for consumer-side deduplication.
 * 
 * Kafka may redeliver events, and the network is unreliable.
 * To maintain idempotency, we record every successfully processed event.
 * If the same event arrives again, we skip reprocessing.
 * 
 * Pattern: Outbox + Inbox
 * - Outbox: Published events in Phase 05
 * - Inbox: Processed events tracking (this table)
 * 
 * Guarantees:
 * - Same event ID processed only once per consumer group
 * - Safe to retry consumer logic without side effects
 * - Supports scaling to multiple consumer instances
 */
@Entity
@Table(name = "processed_events", indexes = {
    @Index(name = "idx_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_aggregate_id", columnList = "aggregate_id"),
    @Index(name = "idx_processed_at", columnList = "processed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    /**
     * Event ID from the outbox event.
     * Globally unique identifier for this event.
     * Used to detect duplicate deliveries.
     */
    @Column(name = "event_id", nullable = false, columnDefinition = "UUID", unique = true)
    private UUID eventId;

    /**
     * Aggregate root ID (notification ID) that triggered this event.
     * Allows tracking which notification this event processed.
     */
    @Column(name = "aggregate_id", nullable = false, columnDefinition = "UUID")
    private UUID aggregateId;

    /**
     * Event type (e.g., "notification.created").
     * Allows filtering processed events by type.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Kafka partition this event was received from.
     * Helps with debugging and understanding message ordering.
     */
    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    /**
     * Kafka offset of this event.
     * Unique within a partition; helps with recovery and debugging.
     */
    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    /**
     * Timestamp when this event was processed by the consumer.
     * Used to track processing latency and debug timing issues.
     */
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    /**
     * Timestamp when this record was created in the database.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }
}
