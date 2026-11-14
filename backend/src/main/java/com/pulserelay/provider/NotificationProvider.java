package com.pulserelay.provider;

import com.pulserelay.provider.model.DeliveryRequest;
import com.pulserelay.provider.model.DeliveryResult;

/**
 * Provider abstraction interface - Phase 09
 * All notification providers must implement this interface
 * Decouples business logic from provider-specific implementations
 */
public interface NotificationProvider {

    /**
     * Get the channel this provider handles
     */
    String getChannel();

    /**
     * Get the provider name
     */
    String getName();

    /**
     * Send notification through this provider
     *
     * @param request Normalized delivery request
     * @return Normalized delivery result
     */
    DeliveryResult send(DeliveryRequest request);

    /**
     * Check if this provider is configured and available
     */
    boolean isAvailable();
}
