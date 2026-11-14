package com.pulserelay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a routing policy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingPolicyRequest {

    /**
     * Event type this policy applies to (e.g., ORDER_CONFIRMED, PAYMENT_FAILED)
     * Required, non-blank, max 100 characters
     */
    @NotBlank(message = "eventType is required")
    private String eventType;

    /**
     * Primary notification channels (comma-separated: EMAIL,SMS,PUSH,IN_APP)
     * Required, non-blank, max 255 characters
     * Valid channels: EMAIL, SMS, PUSH, IN_APP
     */
    @NotBlank(message = "primaryChannels is required")
    private String primaryChannels;

    /**
     * Fallback channels if primary channels fail (comma-separated)
     * Optional, max 255 characters
     */
    private String fallbackChannels;

    /**
     * Priority level (CRITICAL, HIGH, NORMAL, LOW)
     * Required
     */
    @NotNull(message = "priority is required")
    private String priority;

    /**
     * Whether this policy is active
     * Optional, defaults to true
     */
    private Boolean enabled;
}
