package com.pulserelay.service;

import com.pulserelay.dto.NotificationRequest;
import com.pulserelay.entity.Notification;
import com.pulserelay.entity.Tenant;
import com.pulserelay.exception.NotificationException;
import com.pulserelay.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing notifications.
 * Handles validation, persistence, and idempotency.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Submit a new notification or return existing one for duplicate requests.
     * 
     * Idempotency: If a notification with the same tenant + idempotency key exists,
     * return the existing notification instead of creating a duplicate.
     * 
     * @param tenant The authenticated tenant submitting the notification
     * @param idempotencyKey The idempotency key from the request header
     * @param request The notification request
     * @return The notification (either newly created or existing)
     * @throws NotificationException if validation fails
     */
    @Transactional
    public Notification submitNotification(Tenant tenant, String idempotencyKey, NotificationRequest request) {
        // Validate input
        validateNotificationRequest(request);

        // Check for duplicate request (same tenant + same idempotency key)
        Optional<Notification> existingNotification = notificationRepository
            .findByTenantAndIdempotencyKey(tenant, idempotencyKey);

        if (existingNotification.isPresent()) {
            log.info("Idempotent request: Returning existing notification ID {} for tenant {}", 
                existingNotification.get().getId(), tenant.getId());
            return existingNotification.get();
        }

        // Create new notification
        Notification notification = Notification.builder()
            .tenant(tenant)
            .eventType(request.getEventType())
            .recipientEmail(request.getRecipientEmail())
            .recipientPhone(request.getRecipientPhone())
            .recipientUserId(request.getRecipientUserId())
            .requestedChannels(request.getRequestedChannels())
            .priority(Notification.Priority.valueOf(request.getPriority().toUpperCase()))
            .payload(request.getPayload())
            .status(Notification.NotificationStatus.PENDING)
            .idempotencyKey(idempotencyKey)
            .build();

        notification = notificationRepository.save(notification);
        log.info("Notification created: ID {} for tenant {} with event type {}", 
            notification.getId(), tenant.getId(), request.getEventType());

        return notification;
    }

    /**
     * Validate the notification request before persistence.
     * 
     * @param request The notification request
     * @throws NotificationException if validation fails
     */
    private void validateNotificationRequest(NotificationRequest request) {
        // Validate that at least one recipient field is provided
        if ((request.getRecipientEmail() == null || request.getRecipientEmail().isBlank()) &&
            (request.getRecipientPhone() == null || request.getRecipientPhone().isBlank()) &&
            (request.getRecipientUserId() == null || request.getRecipientUserId().isBlank())) {
            throw new NotificationException(
                "At least one of recipientEmail, recipientPhone, or recipientUserId must be provided"
            );
        }

        // Validate priority
        try {
            Notification.Priority.valueOf(request.getPriority().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NotificationException(
                "Invalid priority. Must be one of: CRITICAL, HIGH, NORMAL, LOW"
            );
        }
    }

    /**
     * Get a notification by ID.
     * 
     * @param notificationId The notification ID
     * @return The notification if found
     * @throws NotificationException if notification not found
     */
    @Transactional(readOnly = true)
    public Notification getNotificationById(UUID notificationId) {
        return notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationException("Notification not found: " + notificationId));
    }
}
