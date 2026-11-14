package com.pulserelay.provider.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized delivery result from providers - Phase 09
 * Abstracts provider-specific responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryResult {

    private DeliveryStatus status;

    private String providerMessageId;  // Unique ID from provider (e.g., email message ID)

    private String errorCode;  // Provider error code if failed

    private String errorMessage;  // Human-readable error

    private boolean isRetryable;  // Whether the failure can be retried

    public enum DeliveryStatus {
        ACCEPTED,          // Provider accepted the request
        DELIVERED,         // Message confirmed delivered (not just accepted)
        FAILED,            // Provider rejected or delivery failed
        PERMANENT_FAILURE  // Non-retryable failure
    }
}
