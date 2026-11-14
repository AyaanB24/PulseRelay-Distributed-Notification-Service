package com.pulserelay.provider.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Normalized delivery request for all providers - Phase 09
 * Abstracts provider-specific details from business logic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryRequest {

    private String channel;  // EMAIL, SMS, PUSH, IN_APP

    private String recipient;  // Email address, phone number, device token, etc.

    private String subject;  // For EMAIL

    private String body;  // Main content

    private Map<String, String> metadata;  // Provider-specific metadata
}
