package com.pulserelay.service;

import com.pulserelay.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Outbox Publisher for the Transactional Outbox Pattern with retry support.
 * 
 * Periodically polls the outbox_events table for eligible events and publishes them to Kafka.
 * Eligible events: PENDING or FAILED events within retry limits and backoff period.
 * 
 * Safety guarantees:
 * - Uses PostgreSQL row-level locking (FOR UPDATE SKIP LOCKED) to prevent concurrent processing
 * - Events are never marked PUBLISHED before Kafka acknowledgement
 * - Failed events are retried with exponential backoff
 * - Application crashes don't lose events (they remain in outbox for retry on restart)
 * - Proper exception logging for debugging
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableScheduling
public class OutboxPublisher {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${pulserelay.kafka.topic.notifications:notification-events}")
    private String notificationTopic;

    @Value("${pulserelay.outbox.poll-interval:5000}")
    private Long pollInterval;

    /**
     * Scheduled task to publish eligible outbox events to Kafka.
     * Runs every N seconds (configurable) to find and publish events.
     * 
     * Flow:
     * 1. Query database for eligible events (PENDING or retryable FAILED)
     * 2. Use PostgreSQL row-level locking to claim events atomically
     * 3. For each event, publish to Kafka with synchronous wait
     * 4. On success: mark PUBLISHED
     * 5. On failure: mark FAILED with incremented retry count
     * 6. If max retries exceeded: mark TERMINAL_FAILED
     * 7. Log detailed exception for debugging
     */
    @Scheduled(fixedDelayString = "${pulserelay.outbox.poll-interval:5000}")
    public void publishPendingEvents() {
        try {
            log.debug("Starting outbox publishing cycle (pollInterval: {}ms)", pollInterval);
            
            // Find eligible events with row-level locks (prevents concurrent processing)
            List<OutboxEvent> eligibleEvents = outboxService.findEligibleEventsForPublishing();

            if (eligibleEvents.isEmpty()) {
                log.debug("No eligible outbox events to publish");
                logBacklogStats();
                return;
            }

            log.info("Publisher claiming {} eligible outbox events for publishing", eligibleEvents.size());

            int successCount = 0;
            int failureCount = 0;

            for (OutboxEvent event : eligibleEvents) {
                log.debug("Processing event ID: {}", event.getId());
                try {
                    publishEvent(event);
                    successCount++;
                } catch (Exception exception) {
                    failureCount++;
                    log.error("Unexpected error processing event {}: {}", event.getId(), exception.getMessage(), exception);
                }
            }

            log.info("Publishing cycle complete: {} succeeded, {} failed out of {} events", 
                successCount, failureCount, eligibleEvents.size());
            logBacklogStats();

        } catch (Exception exception) {
            log.error("Critical error in outbox publishing cycle", exception);
        }
    }

    /**
     * Publish a single outbox event to Kafka.
     * 
     * Handles:
     * - Building the Kafka message with event payload and headers
     * - Synchronous sending with broker acknowledgement
     * - Marking as PUBLISHED only after Kafka confirms
     * - Detailed exception logging on failure
     */
    private void publishEvent(OutboxEvent event) {
        try {
            log.info("Sending to Kafka: eventId={}, aggregateId={}, topic={}, attempt={}",
                event.getId(), event.getAggregateId(), notificationTopic, event.getRetryCount() + 1);

            // Build Kafka message with headers
            @SuppressWarnings("unchecked")
            Message<Object> message = (Message<Object>) (Object) MessageBuilder
                .withPayload(event.getPayload())
                .setHeader(KafkaHeaders.TOPIC, notificationTopic)
                .setHeader("kafka_messageKey", event.getAggregateId().toString())
                .setHeader("X-Event-ID", event.getId().toString())
                .setHeader("X-Event-Type", event.getEventType())
                .build();

            log.debug("Kafka message built with headers for event: {}", event.getId());

            // Send to Kafka synchronously and wait for broker acknowledgement
            // This ensures we only mark PUBLISHED after Kafka confirms receipt
            var sendResult = kafkaTemplate.send(message).get();
            log.debug("Kafka broker acknowledged event: partition={}, offset={}, topic={}",
                sendResult.getRecordMetadata().partition(),
                sendResult.getRecordMetadata().offset(),
                sendResult.getRecordMetadata().topic());

            // Only mark as published after successful Kafka acknowledgement
            outboxService.markAsPublished(event.getId());
            log.info("PUBLISHED: eventId={}, aggregateId={}, attempts={}, statusCode=200",
                event.getId(),
                event.getAggregateId(),
                event.getRetryCount() + 1);

        } catch (ExecutionException kafkaException) {
            // ExecutionException wraps the actual Kafka exception
            Throwable cause = kafkaException.getCause();
            String errorMsg = cause != null ? cause.getMessage() : kafkaException.getMessage();
            log.warn("Kafka publish failed - scheduling retry: eventId={}, reason={}, retries={}",
                event.getId(), errorMsg, event.getRetryCount() + 1);
            outboxService.markAsFailed(event.getId(), kafkaException);

        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Kafka send interrupted: eventId={}, reason={}, retries={}",
                event.getId(), interruptedException.getMessage(), event.getRetryCount() + 1);
            outboxService.markAsFailed(event.getId(), interruptedException);

        } catch (Exception exception) {
            log.error("Unexpected error publishing event {}: {}", event.getId(), exception.getMessage(), exception);
            outboxService.markAsFailed(event.getId(), exception);
        }
    }

    /**
     * Log current outbox backlog statistics for monitoring.
     * Includes: pending, retryable failed, and terminal failures.
     * Also logs detailed info about retryable events for debugging.
     */
    private void logBacklogStats() {
        try {
            long pendingCount = outboxService.countPendingEvents();
            long retryableFailedCount = outboxService.countRetryableFailedEvents();
            long terminalFailureCount = outboxService.countTerminalFailures();

            if (pendingCount > 0 || retryableFailedCount > 0 || terminalFailureCount > 0) {
                log.info(
                    "Outbox backlog stats - PENDING: {}, RETRYABLE_FAILED: {}, TERMINAL_FAILED: {}",
                    pendingCount,
                    retryableFailedCount,
                    terminalFailureCount
                );

                // If there are retryable failed events but none are currently eligible,
                // log diagnostic info to help understand why (likely in backoff period)
                if (retryableFailedCount > 0) {
                    outboxService.logRetryableFailedEventsDebugInfo();
                }
            }
        } catch (Exception exception) {
            log.warn("Error logging backlog stats: {}", exception.getMessage());
        }
    }
}
