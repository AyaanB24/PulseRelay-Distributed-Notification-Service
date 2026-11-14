package com.pulserelay.service;

import com.pulserelay.config.RetryPolicyConfig;
import com.pulserelay.entity.DeliveryAttempt;
import com.pulserelay.provider.model.DeliveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Retry Scheduler - Phase 10
 * Manages retry scheduling with exponential backoff
 */
@Service
@Slf4j
public class RetryScheduler {

    private final RetryPolicyConfig retryPolicyConfig;

    @Autowired
    public RetryScheduler(RetryPolicyConfig retryPolicyConfig) {
        this.retryPolicyConfig = retryPolicyConfig;
    }

    /**
     * Determine if a failure should be retried
     */
    public boolean shouldRetry(DeliveryAttempt attempt) {
        // Count how many attempts already exist for this notification
        int attemptCount = getAttemptCount(attempt);
        
        // Check if we've exceeded max attempts
        if (attemptCount >= retryPolicyConfig.getMaxAttempts()) {
            log.info("Notification {} reached max attempts ({}/{})", 
                    attempt.getNotificationId(), attemptCount, retryPolicyConfig.getMaxAttempts());
            return false;
        }

        // Check if failure is retryable
        if (attempt.getIsRetryable() == null || !attempt.getIsRetryable()) {
            log.info("Notification {} has non-retryable failure", attempt.getNotificationId());
            return false;
        }

        // Check if status indicates we should retry
        if (attempt.getStatus() != DeliveryAttempt.DeliveryStatus.FAILED) {
            log.info("Notification {} status {} is not FAILED, no retry", 
                    attempt.getNotificationId(), attempt.getStatus());
            return false;
        }

        return true;
    }

    /**
     * Schedule next retry attempt with exponential backoff
     * Returns the updated DeliveryAttempt with nextAttemptAt set
     */
    public DeliveryAttempt scheduleRetry(DeliveryAttempt failedAttempt) {
        int attemptCount = getAttemptCount(failedAttempt);
        
        if (attemptCount >= retryPolicyConfig.getMaxAttempts()) {
            log.warn("Cannot schedule retry: max attempts reached for notification {}",
                    failedAttempt.getNotificationId());
            return failedAttempt;
        }

        // Calculate delay for next attempt (0-indexed)
        long delayMs = retryPolicyConfig.calculateDelayMs(attemptCount);
        Instant nextAttempt = Instant.now().plusMillis(delayMs);

        failedAttempt.setNextAttemptAt(nextAttempt);
        failedAttempt.setStatus(DeliveryAttempt.DeliveryStatus.RETRY_SCHEDULED);

        log.info("Scheduled retry for notification {}: attempt #{} at {} (delay: {}ms)",
                failedAttempt.getNotificationId(), attemptCount + 1, nextAttempt, delayMs);

        return failedAttempt;
    }

    /**
     * Get the count of attempts for a notification
     * In a real implementation, this would query the database
     * For now, we'll use a simplified approach
     */
    private int getAttemptCount(DeliveryAttempt attempt) {
        // This is a placeholder - in production, query DeliveryAttemptRepository
        // For this implementation, we assume it's managed elsewhere
        return 1;  // Assume first attempt for now
    }

    /**
     * Check if a delivery result should be retried
     */
    public boolean isRetryableResult(DeliveryResult result) {
        return result.isRetryable() && 
               result.getStatus() == DeliveryResult.DeliveryStatus.FAILED;
    }
}
