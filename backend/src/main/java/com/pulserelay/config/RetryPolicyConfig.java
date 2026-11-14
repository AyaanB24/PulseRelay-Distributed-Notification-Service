package com.pulserelay.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Retry Policy Configuration - Phase 10
 * Configures exponential backoff retry behavior
 */
@Component
@ConfigurationProperties(prefix = "pulserelay.retry.policy")
@Getter
@AllArgsConstructor
public class RetryPolicyConfig {
    private int maxAttempts;
    private long initialDelayMs;
    private long maxDelayMs;
    private double backoffMultiplier;

    public RetryPolicyConfig() {
        this.maxAttempts = 5;
        this.initialDelayMs = 1000;
        this.maxDelayMs = 60000;
        this.backoffMultiplier = 2.0;
    }

    /**
     * Calculate delay for attempt number (0-indexed)
     * Formula: min(initialDelay * (multiplier ^ attempt), maxDelay)
     */
    public long calculateDelayMs(int attemptNumber) {
        long delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attemptNumber));
        return Math.min(delay, maxDelayMs);
    }
}
