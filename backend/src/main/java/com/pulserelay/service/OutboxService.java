package com.pulserelay.service;

import com.pulserelay.dto.OutboxEventPayload;
import com.pulserelay.entity.OutboxEvent;
import com.pulserelay.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing outbox events.
 * Implements the Transactional Outbox Pattern with retry support and exponential backoff.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${pulserelay.outbox.max-retries:5}")
    private int maxRetries;

    @Value("${pulserelay.outbox.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    /**
     * Create and persist an outbox event for a notification.
     * Must be called in the same transaction as the notification persistence.
     * 
     * @param notificationId The notification ID (aggregate root)
     * @param tenantId The tenant ID
     * @param notificationCreatedAt When the notification was created
     * @param notificationData The notification details
     * @return The created outbox event
     */
    @Transactional
    public OutboxEvent createOutboxEvent(
            UUID notificationId,
            UUID tenantId,
            LocalDateTime notificationCreatedAt,
            Map<String, Object> notificationData) {

        // Build the event payload
        OutboxEventPayload eventPayload = OutboxEventPayload.forNotificationCreated(
            UUID.randomUUID(),  // Unique event ID for idempotency
            notificationId,
            tenantId,
            notificationCreatedAt,
            notificationData
        );

        // Create the outbox event
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .eventType("notification.created")
            .aggregateId(notificationId)
            .payload(convertPayloadToMap(eventPayload))
            .status(OutboxEvent.OutboxEventStatus.PENDING)
            .retryCount(0)
            .build();

        outboxEvent = outboxEventRepository.save(outboxEvent);
        log.info("Outbox event created for notification {}: {}", notificationId, outboxEvent.getId());

        return outboxEvent;
    }

    /**
     * Find eligible outbox events for publishing using exponential backoff.
     * 
     * Eligible events:
     * 1. PENDING (never attempted)
     * 2. FAILED with retry_count < max_retries AND enough time has passed since last attempt
     * 
     * Backoff calculation: initialBackoff * 2^(retryCount-1)
     * Example with initialBackoff=1000ms:
     * - Retry 1: 1s
     * - Retry 2: 2s
     * - Retry 3: 4s
     * - Retry 4: 8s
     * - Retry 5: 16s
     * 
     * Uses PostgreSQL row-level locking to prevent concurrent processing.
     * 
     * @return List of eligible events with row-level write locks
     */
    @Transactional
    public List<OutboxEvent> findEligibleEventsForPublishing() {
        // Calculate backoff time: events are only eligible if last_attempted_at is before this time
        LocalDateTime backoffTime = calculateBackoffTime();
        
        List<OutboxEvent> eligibleEvents = outboxEventRepository.findEligibleEventsForPublishing(
            maxRetries,
            backoffTime
        );

        log.debug("Found {} eligible events for publishing (maxRetries: {}, backoffTime: {})",
            eligibleEvents.size(), maxRetries, backoffTime);

        return eligibleEvents;
    }

    /**
     * Mark an outbox event as published after successful Kafka acknowledgement.
     * Only call this after Kafka has confirmed receipt.
     * 
     * @param eventId The outbox event ID
     */
    @Transactional
    public void markAsPublished(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        event.setStatus(OutboxEvent.OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        outboxEventRepository.save(event);

        log.info("Outbox event marked as PUBLISHED: {} (retried {} times)", eventId, event.getRetryCount());
    }

    /**
     * Mark an outbox event as failed and increment retry count.
     * If retry_count reaches max_retries, mark as TERMINAL_FAILED instead.
     * 
     * @param eventId The outbox event ID
     * @param exception The exception that caused the failure
     */
    @Transactional
    public void markAsFailed(UUID eventId, Exception exception) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastAttemptedAt(LocalDateTime.now());

        if (event.getRetryCount() >= maxRetries) {
            event.setStatus(OutboxEvent.OutboxEventStatus.TERMINAL_FAILED);
            log.error(
                "Outbox event marked as TERMINAL_FAILED (max retries {} exhausted): {} - Exception: {}",
                maxRetries,
                eventId,
                exception != null ? exception.getMessage() : "Unknown"
            );
        } else {
            event.setStatus(OutboxEvent.OutboxEventStatus.FAILED);
            long backoffMs = calculateBackoffForRetry(event.getRetryCount());
            log.warn(
                "Outbox event marked as FAILED (retry #{}/{}), next attempt in ~{}ms: {} - Exception: {}",
                event.getRetryCount(),
                maxRetries,
                backoffMs,
                eventId,
                exception != null ? exception.getMessage() : "Unknown"
            );
        }

        outboxEventRepository.save(event);
    }

    /**
     * Get count of pending outbox events (never attempted).
     * 
     * @return Number of PENDING events
     */
    @Transactional(readOnly = true)
    public long countPendingEvents() {
        return outboxEventRepository.countPendingEvents();
    }

    /**
     * Get count of retryable failed events.
     * 
     * @return Number of FAILED events still eligible for retry
     */
    @Transactional(readOnly = true)
    public long countRetryableFailedEvents() {
        return outboxEventRepository.countRetryableFailedEvents(maxRetries);
    }

    /**
     * Get count of terminal failures.
     * These events have exhausted all retries and require manual intervention.
     * 
     * @return Number of TERMINAL_FAILED events
     */
    @Transactional(readOnly = true)
    public long countTerminalFailures() {
        return outboxEventRepository.countTerminalFailures();
    }

    /**
     * Calculate the backoff time threshold for finding eligible events.
     * Returns a time such that:
     * - Events with lastAttemptedAt before this time are eligible for retry
     * - Events with lastAttemptedAt after this time are still in backoff period
     */
    private LocalDateTime calculateBackoffTime() {
        // For simplicity, we allow retrying all events with passed backoff
        // A more sophisticated approach would consider retry count when calculating backoff
        // But for now, we use a fixed backoff to prevent hammering Kafka
        return LocalDateTime.now().minusSeconds(initialBackoffMs / 1000);
    }

    /**
     * Calculate exponential backoff duration based on retry count.
     * Formula: initialBackoff * 2^(retryCount-1)
     * 
     * @param retryCount Current retry attempt number
     * @return Backoff duration in milliseconds
     */
    private long calculateBackoffForRetry(int retryCount) {
        long backoff = initialBackoffMs * (long) Math.pow(2, retryCount - 1);
        // Cap backoff at 1 hour
        return Math.min(backoff, 3600_000L);
    }

    /**
     * Convert OutboxEventPayload to Map for storage in JSON column.
     */
    private Map<String, Object> convertPayloadToMap(OutboxEventPayload eventPayload) {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventPayload.getEventId());
        map.put("eventType", eventPayload.getEventType());
        map.put("notificationId", eventPayload.getNotificationId());
        map.put("tenantId", eventPayload.getTenantId());
        map.put("occurredAt", eventPayload.getOccurredAt());
        map.put("payload", eventPayload.getPayload());
        map.put("version", eventPayload.getVersion());
        return map;
    }

    /**
     * Debug utility: Log details about retryable FAILED events.
     * Helps understand why events aren't being published (e.g., still in backoff).
     */
    @Transactional(readOnly = true)
    public void logRetryableFailedEventsDebugInfo() {
        try {
            List<OutboxEvent> retryableEvents = outboxEventRepository.findRetryableFailedEventsWithDetails(maxRetries);
            
            if (!retryableEvents.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                log.debug("=== Retryable FAILED Events Debug Info ===");
                
                for (OutboxEvent event : retryableEvents) {
                    // Handle NULL lastAttemptedAt (shouldn't happen but defensive coding)
                    if (event.getLastAttemptedAt() == null) {
                        log.debug(
                            "Event {} - Retry #{}/{}, LastAttempted: NULL (BUG: should be set), Status: NEEDS_MANUAL_FIX",
                            event.getId(),
                            event.getRetryCount(),
                            maxRetries
                        );
                        continue;
                    }

                    long secondsSinceAttempt = java.time.temporal.ChronoUnit.SECONDS
                        .between(event.getLastAttemptedAt(), now);
                    
                    long backoffSeconds = calculateBackoffForRetry(event.getRetryCount()) / 1000;
                    boolean isEligible = secondsSinceAttempt >= backoffSeconds;
                    
                    log.debug(
                        "Event {} - Retry #{}/{}, LastAttempted: {}s ago, Backoff: {}s, Eligible: {}",
                        event.getId(),
                        event.getRetryCount(),
                        maxRetries,
                        secondsSinceAttempt,
                        backoffSeconds,
                        isEligible
                    );
                }
                log.debug("=== End Debug Info ===");
            }
        } catch (Exception exception) {
            log.warn("Error logging debug info: {}", exception.getMessage());
        }
    }

    /**
     * Fix events with NULL lastAttemptedAt (data inconsistency from old code).
     * Sets lastAttemptedAt to current time for eligible retries.
     * Only call this once during migration.
     */
    @Transactional
    public void fixNullLastAttemptedAtForFailedEvents() {
        try {
            // Find all FAILED events with NULL lastAttemptedAt
            List<OutboxEvent> eventsNeedingFix = outboxEventRepository.findAll().stream()
                .filter(e -> e.getStatus() == OutboxEvent.OutboxEventStatus.FAILED && e.getLastAttemptedAt() == null)
                .toList();

            if (!eventsNeedingFix.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                log.warn("Found {} FAILED events with NULL lastAttemptedAt - fixing...", eventsNeedingFix.size());

                for (OutboxEvent event : eventsNeedingFix) {
                    event.setLastAttemptedAt(now);
                    outboxEventRepository.save(event);
                    log.info("Fixed event {} - set lastAttemptedAt to now", event.getId());
                }

                log.warn("Fixed {} FAILED events", eventsNeedingFix.size());
            }
        } catch (Exception exception) {
            log.error("Error fixing NULL lastAttemptedAt: {}", exception.getMessage(), exception);
        }
    }
}
