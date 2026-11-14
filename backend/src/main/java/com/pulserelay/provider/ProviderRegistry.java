package com.pulserelay.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider registry - Phase 09
 * Manages available providers and routes delivery requests to appropriate provider
 */
@Component
@Slf4j
public class ProviderRegistry {

    private final Map<String, NotificationProvider> providers = new HashMap<>();

    @Autowired
    public ProviderRegistry(List<NotificationProvider> providerList) {
        for (NotificationProvider provider : providerList) {
            if (provider.isAvailable()) {
                providers.put(provider.getChannel(), provider);
                log.info("Registered provider: {} for channel: {}", provider.getName(), provider.getChannel());
            }
        }
    }

    /**
     * Get provider for a channel
     */
    public Optional<NotificationProvider> getProvider(String channel) {
        return Optional.ofNullable(providers.get(channel));
    }

    /**
     * Check if provider is available for channel
     */
    public boolean isAvailable(String channel) {
        return providers.containsKey(channel);
    }

    /**
     * Get all available channels
     */
    public List<String> getAvailableChannels() {
        return List.copyOf(providers.keySet());
    }
}
