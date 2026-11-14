package com.pulserelay.service;

import com.pulserelay.entity.DeliveryAttempt;
import com.pulserelay.repository.DeliveryAttemptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Retry Executor - Phase 10
 * Executes scheduled retries at appropriate times
 */
@Service
@EnableScheduling
@Slf4j
public class RetryExecutor {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryService deliveryService;

    @Autowired
    public RetryExecutor(DeliveryAttemptRepository deliveryAttemptRepository,
                         DeliveryService deliveryService) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryService = deliveryService;
    }

    /**
     * Scheduled task: Execute retries due now
     * Runs every 30 seconds to check for due retries
     */
    @Scheduled(fixedDelay = 30000)  // Run every 30 seconds
    @Transactional
    public void executeScheduledRetries() {
        log.debug("Checking for scheduled retries");

        // Find all attempts scheduled for retry that are due now
        Instant now = Instant.now();
        List<DeliveryAttempt> dueRetries = deliveryAttemptRepository
                .findByStatusAndNextAttemptAtBefore(
                        DeliveryAttempt.DeliveryStatus.RETRY_SCHEDULED, 
                        now);

        if (dueRetries.isEmpty()) {
            return;
        }

        log.info("Found {} delivery attempts due for retry", dueRetries.size());

        for (DeliveryAttempt attempt : dueRetries) {
            try {
                executeRetry(attempt);
            } catch (Exception e) {
                log.error("Error executing retry for attempt {}: {}", attempt.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Execute a single retry
     */
    @Transactional
    private void executeRetry(DeliveryAttempt previousAttempt) {
        log.info("Executing retry for notification {}, previous attempt: {}",
                previousAttempt.getNotificationId(), previousAttempt.getId());

        if (previousAttempt.getNotificationId() == null) {
            log.warn("Cannot retry delivery attempt with null notificationId: {}", 
                    previousAttempt.getId());
            return;
        }

        // Re-execute delivery
        DeliveryAttempt newAttempt = deliveryService.executeDelivery(
                previousAttempt.getTenantId(),
                previousAttempt.getNotificationId(),
                previousAttempt.getChannel(),
                previousAttempt.getRecipient(),
                "",  // subject not needed for retry
                ""   // body not needed for retry
        );

        log.info("Retry attempt {} completed with status: {}",
                newAttempt.getId(), newAttempt.getStatus());
    }

    /**
     * Find retryable failed attempts (for manual/admin retry)
     */
    public List<DeliveryAttempt> findRetryableFailed() {
        return deliveryAttemptRepository
                .findByStatusAndIsRetryable(
                        DeliveryAttempt.DeliveryStatus.FAILED, 
                        true);
    }

    /**
     * Manually retry a failed attempt
     */
    @Transactional
    public DeliveryAttempt manualRetry(DeliveryAttempt failedAttempt) {
        if (!failedAttempt.getIsRetryable() || 
            failedAttempt.getStatus() != DeliveryAttempt.DeliveryStatus.FAILED) {
            throw new IllegalArgumentException("Attempt is not eligible for manual retry");
        }

        log.info("Manual retry initiated for notification {}", 
                failedAttempt.getNotificationId());

        return deliveryService.executeDelivery(
                failedAttempt.getTenantId(),
                failedAttempt.getNotificationId(),
                failedAttempt.getChannel(),
                failedAttempt.getRecipient(),
                "",
                ""
        );
    }
}
