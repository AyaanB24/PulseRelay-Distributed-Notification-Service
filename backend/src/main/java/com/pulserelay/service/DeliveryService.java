package com.pulserelay.service;

import com.pulserelay.entity.DeliveryAttempt;
import com.pulserelay.provider.NotificationProvider;
import com.pulserelay.provider.ProviderRegistry;
import com.pulserelay.provider.model.DeliveryRequest;
import com.pulserelay.provider.model.DeliveryResult;
import com.pulserelay.repository.DeliveryAttemptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Delivery service - Phase 09
 * Coordinates routing policy resolution, template selection/rendering,
 * provider selection, and delivery execution
 */
@Service
@Slf4j
public class DeliveryService {

    private final ProviderRegistry providerRegistry;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    public DeliveryService(
            ProviderRegistry providerRegistry,
            DeliveryAttemptRepository deliveryAttemptRepository) {
        this.providerRegistry = providerRegistry;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
    }

    /**
     * Execute delivery through the appropriate provider
     * Integrates: routing policy -> template rendering -> provider selection -> delivery execution
     *
     * @param tenantId Tenant ID
     * @param notificationId Notification ID
     * @param channel Channel (EMAIL, SMS, PUSH, IN_APP)
     * @param recipient Destination (email, phone, etc.)
     * @param subject Subject (for EMAIL)
     * @param body Body content
     * @return Delivery attempt record
     */
    @Transactional
    public DeliveryAttempt executeDelivery(
            UUID tenantId,
            UUID notificationId,
            String channel,
            String recipient,
            String subject,
            String body) {

        log.info("Executing delivery: notificationId={}, channel={}, recipient={}", 
                notificationId, channel, recipient);

        // Get provider for this channel
        NotificationProvider provider = providerRegistry.getProvider(channel)
                .orElseThrow(() -> new IllegalArgumentException("No provider available for channel: " + channel));

        // Build normalized delivery request
        DeliveryRequest deliveryRequest = DeliveryRequest.builder()
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .build();

        // Execute delivery through provider
        DeliveryResult result;
        try {
            result = provider.send(deliveryRequest);
        } catch (Exception e) {
            log.error("Provider execution failed: {}", e.getMessage(), e);
            result = DeliveryResult.builder()
                    .status(DeliveryResult.DeliveryStatus.PERMANENT_FAILURE)
                    .errorCode("PROVIDER_ERROR")
                    .errorMessage(e.getMessage())
                    .isRetryable(false)
                    .build();
        }

        // Record delivery attempt
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .notificationId(notificationId)
                .tenantId(tenantId)
                .channel(channel)
                .provider(provider.getName())
                .recipient(recipient)
                .status(mapResultStatus(result.getStatus()))
                .errorCode(result.getErrorCode())
                .errorMessage(result.getErrorMessage())
                .providerMessageId(result.getProviderMessageId())
                .isRetryable(result.isRetryable())
                .build();

        attempt = deliveryAttemptRepository.save(attempt);
        log.info("Delivery attempt recorded: id={}, status={}", attempt.getId(), attempt.getStatus());

        return attempt;
    }

    /**
     * Get delivery status for a notification
     */
    @Transactional(readOnly = true)
    public DeliveryAttempt.DeliveryStatus getNotificationStatus(UUID notificationId) {
        var attempts = deliveryAttemptRepository.findByNotificationId(notificationId);

        if (attempts.isEmpty()) {
            return null;
        }

        // Determine overall status from all attempts
        // Priority: DELIVERED > PERMANENT_FAILURE > FAILED > ACCEPTED
        for (DeliveryAttempt attempt : attempts) {
            if (attempt.getStatus() == DeliveryAttempt.DeliveryStatus.DELIVERED) {
                return DeliveryAttempt.DeliveryStatus.DELIVERED;
            }
        }

        for (DeliveryAttempt attempt : attempts) {
            if (attempt.getStatus() == DeliveryAttempt.DeliveryStatus.PERMANENT_FAILURE) {
                return DeliveryAttempt.DeliveryStatus.PERMANENT_FAILURE;
            }
        }

        for (DeliveryAttempt attempt : attempts) {
            if (attempt.getStatus() == DeliveryAttempt.DeliveryStatus.FAILED) {
                return DeliveryAttempt.DeliveryStatus.FAILED;
            }
        }

        return DeliveryAttempt.DeliveryStatus.ACCEPTED;
    }

    private DeliveryAttempt.DeliveryStatus mapResultStatus(DeliveryResult.DeliveryStatus resultStatus) {
        return switch (resultStatus) {
            case ACCEPTED -> DeliveryAttempt.DeliveryStatus.ACCEPTED;
            case DELIVERED -> DeliveryAttempt.DeliveryStatus.DELIVERED;
            case FAILED -> DeliveryAttempt.DeliveryStatus.FAILED;
            case PERMANENT_FAILURE -> DeliveryAttempt.DeliveryStatus.PERMANENT_FAILURE;
        };
    }
}
