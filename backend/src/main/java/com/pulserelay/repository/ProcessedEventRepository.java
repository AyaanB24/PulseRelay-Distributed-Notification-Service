package com.pulserelay.repository;

import com.pulserelay.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProcessedEvent entity.
 * Handles deduplication tracking for Kafka consumer.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Check if an event has already been processed.
     * 
     * @param eventId The event ID from outbox/Kafka
     * @return True if this event was already processed by the consumer
     */
    boolean existsByEventId(UUID eventId);

    /**
     * Find a previously processed event by its ID.
     * 
     * @param eventId The event ID
     * @return The processed event record if it exists
     */
    Optional<ProcessedEvent> findByEventId(UUID eventId);
}
