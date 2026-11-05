package com.pulserelay.controller;

import com.pulserelay.config.TenantPrincipal;
import com.pulserelay.dto.NotificationRequest;
import com.pulserelay.dto.NotificationResponse;
import com.pulserelay.entity.Notification;
import com.pulserelay.entity.Tenant;
import com.pulserelay.service.NotificationService;
import com.pulserelay.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Notification endpoints for accepting notification requests.
 * All endpoints require X-API-KEY authentication.
 * Tenant information is extracted from the authenticated security context.
 */
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final TenantService tenantService;

    /**
     * Submit a new notification.
     * 
     * Endpoint: POST /api/v1/notifications
     * 
     * Headers:
     *   X-API-KEY: The tenant's API key (required)
     *   Idempotency-Key: Unique key for idempotency (required)
     * 
     * Request body:
     * {
     *   "eventType": "order_confirmation",
     *   "recipientEmail": "customer@example.com",
     *   "recipientPhone": "+1234567890",
     *   "recipientUserId": "user_12345",
     *   "requestedChannels": "EMAIL,SMS",
     *   "priority": "HIGH",
     *   "payload": {
     *     "orderId": "ORD-12345",
     *     "amount": 99.99,
     *     "currency": "USD"
     *   }
     * }
     * 
     * Response (202 Accepted):
     * {
     *   "notificationId": "550e8400-e29b-41d4-a716-446655440000",
     *   "status": "PENDING",
     *   "message": "Notification accepted for processing",
     *   "createdAt": "2026-11-05T12:34:56.123456"
     * }
     * 
     * Response (400 Bad Request):
     * - Invalid request body (validation errors)
     * - Missing required fields
     * - Invalid priority value
     * - No recipient specified (email/phone/userId)
     * 
     * Response (400 Bad Request - Missing Idempotency Key):
     * {
     *   "error": "Idempotency-Key header is required",
     *   "status": 400
     * }
     * 
     * Response (401 Unauthorized):
     * - Missing or invalid X-API-KEY
     * - Associated tenant is SUSPENDED
     * 
     * Response (409 Conflict - Duplicate):
     * When a duplicate request is received (same tenant + same key),
     * the response will still be 202 Accepted with the existing notification ID.
     * 
     * @param idempotencyKey The Idempotency-Key header (required for deduplication)
     * @param request The notification request
     * @return NotificationResponse with notificationId and status
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> submitNotification(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NotificationRequest request) {

        try {
            // Validate idempotency key
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                log.warn("Missing Idempotency-Key header");
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(null);
            }

            // Extract authenticated tenant from SecurityContext
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("No authenticated principal found in security context");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Object principal = authentication.getPrincipal();
            if (!(principal instanceof TenantPrincipal)) {
                log.error("Principal is not a TenantPrincipal: {}", principal.getClass().getName());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            TenantPrincipal tenantPrincipal = (TenantPrincipal) principal;

            // Retrieve the authenticated tenant
            Tenant tenant = tenantService.getTenantById(tenantPrincipal.getTenantId());

            // Submit notification (handles idempotency internally)
            Notification notification = notificationService.submitNotification(tenant, idempotencyKey, request);

            // Determine if this is a new or duplicate notification
            boolean isDuplicate = notification.getCreatedAt().equals(notification.getUpdatedAt()) == false;

            log.debug("Notification submitted for tenant {}: ID {}", tenant.getId(), notification.getId());

            // Return 202 Accepted with notification details
            NotificationResponse response = isDuplicate ?
                NotificationResponse.duplicate(notification.getId(), notification.getCreatedAt()) :
                NotificationResponse.created(notification.getId(), notification.getCreatedAt());

            return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);

        } catch (Exception exception) {
            log.error("Unexpected error while submitting notification", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
