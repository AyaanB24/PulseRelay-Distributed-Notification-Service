package com.pulserelay.service;

import com.pulserelay.entity.Notification;
import com.pulserelay.entity.ProcessedEvent;
import com.pulserelay.repository.NotificationRepository;
import com.pulserelay.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for processing notification events from Kafka.
 * 
 * Responsibilities:
 * 1. Deserialize and validate event payloads
 * 2. Load notifications from database
 * 3. Check for duplicate processing (idempotency)
 * 4. Update notification status to PROCESSING
 * 5. Record the processed event for deduplication
 * 
 * Guarantees:
 * - Duplicate events are not reprocessed
 * - Processing is transactional and all-or-nothing
 * - Database remains consistent even on consumer failure
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationProcessingService {

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * Process a notification event from Kafka.
     * 
     * Flow:
     * 1. Check if this event was already processed (deduplication)
     * 2. If duplicate: Log and return without reprocessing
     * 3. If new: Load notification from database
     * 4. Validate notification exists
     * 5. Mark notification as PROCESSING
     * 6. Record this event as processed
     * 7. Commit transaction (transactional outbox semantics)
     * 
     * @param eventId Unique event ID from outbox
     * @param aggregateId Notification ID that triggered this event
     * @param eventType Type of event (e.g., "notification.created")
     * @param payload Event payload containing notification data
     * @param kafkaPartition Kafka partition this message came from
     * @param kafkaOffset Kafka offset of this message
     * @throws IllegalArgumentException If notification not found
     */
    @Transactional
    public void processNotificationEvent(
            UUID eventId,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload,
            Integer kafkaPartition,
            Long kafkaOffset) {

        log.debug("Processing event: eventId={}, aggregateId={}, eventType={}, partition={}, offset={}",
            eventId, aggregateId, eventType, kafkaPartition, kafkaOffset);

        // Check if already processed (deduplication)
        if (isEventAlreadyProcessed(eventId)) {
            log.info("Event already processed (duplicate), skipping: eventId={}, aggregateId={}",
                eventId, aggregateId);
            return;
        }

        // Load notification from database
        Notification notification = notificationRepository.findById(aggregateId)
            .orElseThrow(() -> {
                log.error("Notification not found for event: eventId={}, aggregateId={}", eventId, aggregateId);
                return new IllegalArgumentException("Notification not found: " + aggregateId);
            });

        log.debug("Loaded notification: id={}, status={}, tenant={}", 
            notification.getId(), notification.getStatus(), notification.getTenant().getId());

        // Update notification status to PROCESSING
        notification.setStatus(Notification.NotificationStatus.PROCESSING);
        Notification updated = notificationRepository.save(notification);

        log.info("Notification status updated to PROCESSING: notificationId={}, eventId={}", 
            notification.getId(), eventId);

        // Record this event as processed (for deduplication)
        ProcessedEvent processedEvent = ProcessedEvent.builder()
            .eventId(eventId)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .kafkaPartition(kafkaPartition)
            .kafkaOffset(kafkaOffset)
            .processedAt(LocalDateTime.now())
            .build();

        processedEventRepository.save(processedEvent);

        log.info("Event recorded as processed: eventId={}, notificationId={}, " +
                "eventType={}, partition={}, offset={}",
            eventId, aggregateId, eventType, kafkaPartition, kafkaOffset);
    }

    /**
     * Check if an event has already been processed.
     * 
     * @param eventId The event ID to check
     * @return True if this event was already processed, false if new
     */
    private boolean isEventAlreadyProcessed(UUID eventId) {
        boolean exists = processedEventRepository.existsByEventId(eventId);
        
        if (exists) {
            Optional<ProcessedEvent> processed = processedEventRepository.findByEventId(eventId);
            if (processed.isPresent()) {
                log.debug("Event was previously processed at: {}, partition={}, offset={}",
                    processed.get().getProcessedAt(),
                    processed.get().getKafkaPartition(),
                    processed.get().getKafkaOffset());
            }
        }
        
        return exists;
    }
}
