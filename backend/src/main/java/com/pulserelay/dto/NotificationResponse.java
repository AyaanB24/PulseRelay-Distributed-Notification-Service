package com.pulserelay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for notification submission.
 * Returned by POST /api/v1/notifications endpoint (HTTP 202 Accepted).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    /**
     * Unique identifier for the notification.
     * Can be used to track the notification status later.
     */
    private UUID notificationId;

    /**
     * Current status of the notification.
     * PENDING: Received and queued for processing.
     */
    private String status;

    /**
     * Human-readable message about the submission.
     * Examples: "Notification accepted for processing"
     *           "Duplicate notification (existing ID: ...)"
     */
    private String message;

    /**
     * Timestamp when the notification was created.
     * ISO-8601 format.
     */
    private LocalDateTime createdAt;

    /**
     * Create a response for a new notification (202 Accepted).
     */
    public static NotificationResponse created(UUID notificationId, LocalDateTime createdAt) {
        return NotificationResponse.builder()
            .notificationId(notificationId)
            .status("PENDING")
            .message("Notification accepted for processing")
            .createdAt(createdAt)
            .build();
    }

    /**
     * Create a response for a duplicate notification (idempotency).
     */
    public static NotificationResponse duplicate(UUID existingNotificationId, LocalDateTime createdAt) {
        return NotificationResponse.builder()
            .notificationId(existingNotificationId)
            .status("PENDING")
            .message("Duplicate notification. Using existing notification ID: " + existingNotificationId)
            .createdAt(createdAt)
            .build();
    }
}
