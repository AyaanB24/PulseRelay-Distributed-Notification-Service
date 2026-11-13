package com.pulserelay.repository;

import com.pulserelay.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OutboxEvent entity.
 * Handles database queries for the Transactional Outbox Pattern with retry support.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Find eligible outbox events for publishing (PENDING or retryable FAILED).
     * 
     * Eligible events:
     * 1. PENDING: Never attempted (lastAttemptedAt is NULL, always eligible)
     * 2. FAILED with retry_count < max_retries AND (lastAttemptedAt IS NULL OR lastAttemptedAt < backoffTime)
     * 
     * NULL handling policy:
     * - FAILED events with NULL lastAttemptedAt are treated as old attempts (eligible immediately)
     * - Rationale: Historical events from before retry tracking should be retried once
     * - After first retry attempt, lastAttemptedAt is set and backoff is respected
     * 
     * Uses PostgreSQL row-level locking (FOR UPDATE SKIP LOCKED) to prevent concurrent
     * processing of the same event by multiple publisher instances.
     * 
     * Generated SQL:
     * SELECT e FROM OutboxEvent e 
     * WHERE (
     *   e.status = 'PENDING'
     *   OR (
     *     e.status = 'FAILED'
     *     AND e.retryCount < :maxRetries
     *     AND (e.lastAttemptedAt IS NULL OR e.lastAttemptedAt < :backoffTime)
     *   )
     * )
     * ORDER BY e.createdAt ASC
     * FOR UPDATE SKIP LOCKED
     * 
     * @param maxRetries Maximum number of retry attempts allowed
     * @param backoffTime Events are only retried if lastAttemptedAt is before this time
     * @return List of eligible events with row-level locks acquired
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE (e.status = 'PENDING' OR (e.status = 'FAILED' AND e.retryCount < :maxRetries AND (e.lastAttemptedAt IS NULL OR e.lastAttemptedAt < :backoffTime)))
        ORDER BY e.createdAt ASC
        """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findEligibleEventsForPublishing(
            @Param("maxRetries") int maxRetries,
            @Param("backoffTime") LocalDateTime backoffTime);

    /**
     * Diagnostic query: Find all retryable FAILED events with their attempt times.
     * Used for debugging and understanding why events aren't being retried.
     * 
     * @param maxRetries Maximum retry attempts
     * @return List of FAILED events that could be retried
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.status = 'FAILED' AND e.retryCount < :maxRetries
        ORDER BY e.lastAttemptedAt ASC
        """)
    List<OutboxEvent> findRetryableFailedEventsWithDetails(@Param("maxRetries") int maxRetries);

    /**
     * Find an outbox event by aggregate ID (notification ID).
     * Used for tracing and debugging: to find events related to a notification.
     * 
     * @param aggregateId The notification ID
     * @return Optional containing the outbox event if found
     */
    Optional<OutboxEvent> findByAggregateId(UUID aggregateId);

    /**
     * Count pending outbox events (never attempted).
     * Used for monitoring.
     * 
     * @return Number of PENDING events
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'PENDING'")
    long countPendingEvents();

    /**
     * Count failed outbox events that are still retryable.
     * Used for monitoring the retry queue.
     * 
     * @param maxRetries Maximum retry attempts
     * @return Number of retryable FAILED events
     */
    @Query("""
        SELECT COUNT(e) FROM OutboxEvent e 
        WHERE e.status = 'FAILED' AND e.retryCount < :maxRetries
        """)
    long countRetryableFailedEvents(@Param("maxRetries") int maxRetries);

    /**
     * Count terminal failures (events that exceeded max retries).
     * Used for alerting and manual intervention.
     * 
     * @return Number of TERMINAL_FAILED events
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'TERMINAL_FAILED'")
    long countTerminalFailures();
}
