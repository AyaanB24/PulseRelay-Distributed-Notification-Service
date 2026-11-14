package com.pulserelay.provider;

import com.pulserelay.provider.model.DeliveryRequest;
import com.pulserelay.provider.model.DeliveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock email provider for local testing and development - Phase 09
 * Simulates different delivery scenarios without sending real emails
 */
@Component
@ConditionalOnProperty(name = "pulserelay.provider.email.type", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockEmailProvider implements NotificationProvider {

    private static final String CHANNEL = "EMAIL";
    private static final String PROVIDER_NAME = "MockEmail";

    // Deterministic test scenarios based on recipient email
    // Example: test-success@mock.local -> always succeeds
    //          test-failure@mock.local -> always fails
    //          test-retry@mock.local -> fails but retryable

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public DeliveryResult send(DeliveryRequest request) {
        String recipient = request.getRecipient();
        log.info("Mock email provider: sending to {}", recipient);

        // Simulate different scenarios based on recipient address
        if (recipient.contains("success")) {
            return DeliveryResult.builder()
                    .status(DeliveryResult.DeliveryStatus.DELIVERED)
                    .providerMessageId("mock-" + UUID.randomUUID())
                    .isRetryable(false)
                    .build();
        } else if (recipient.contains("transient-failure")) {
            return DeliveryResult.builder()
                    .status(DeliveryResult.DeliveryStatus.FAILED)
                    .errorCode("TRANSIENT_ERROR")
                    .errorMessage("Mock transient failure - service temporarily unavailable")
                    .isRetryable(true)
                    .build();
        } else if (recipient.contains("permanent-failure")) {
            return DeliveryResult.builder()
                    .status(DeliveryResult.DeliveryStatus.PERMANENT_FAILURE)
                    .errorCode("INVALID_RECIPIENT")
                    .errorMessage("Mock permanent failure - invalid email address")
                    .isRetryable(false)
                    .build();
        } else {
            // Default: success
            return DeliveryResult.builder()
                    .status(DeliveryResult.DeliveryStatus.DELIVERED)
                    .providerMessageId("mock-" + UUID.randomUUID())
                    .isRetryable(false)
                    .build();
        }
    }

    @Override
    public boolean isAvailable() {
        return true;  // Mock provider is always available
    }
}
