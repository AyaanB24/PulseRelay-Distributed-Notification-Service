package com.pulserelay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for submitting a notification.
 * Validates input before persistence to PostgreSQL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    /**
     * Type of event being notified (e.g., order_confirmation, payment_success).
     * Required, non-blank, max 100 characters.
     */
    @NotBlank(message = "eventType is required")
    private String eventType;

    /**
     * Email address of the notification recipient.
     * Optional — at least one of email, phone, or userId must be provided.
     */
    private String recipientEmail;

    /**
     * Phone number of the notification recipient.
     * Optional — at least one of email, phone, or userId must be provided.
     */
    private String recipientPhone;

    /**
     * User ID of the notification recipient (tenant's internal user ID).
     * Optional — at least one of email, phone, or userId must be provided.
     */
    private String recipientUserId;

    /**
     * Comma-separated list of requested channels (e.g., "EMAIL,SMS" or "EMAIL").
     * Required, non-blank, max 255 characters.
     * Valid channels: EMAIL, SMS, PUSH, IN_APP
     */
    @NotBlank(message = "requestedChannels is required")
    private String requestedChannels;

    /**
     * Priority level for notification delivery.
     * Required. Valid values: CRITICAL, HIGH, NORMAL, LOW
     */
    @NotNull(message = "priority is required")
    private String priority;

    /**
     * JSON payload/data for the notification.
     * Contains template variables and content.
     * Optional — notification may have no data.
     */
    private Map<String, Object> payload;
}
