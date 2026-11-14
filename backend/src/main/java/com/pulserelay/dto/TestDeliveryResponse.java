package com.pulserelay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * TestDeliveryResponse DTO - Phase 09
 * Response from test delivery endpoint
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TestDeliveryResponse {

    private UUID id;

    private String status;  // ACCEPTED, DELIVERED, FAILED, PERMANENT_FAILURE

    private String errorCode;

    private String errorMessage;

    private String providerMessageId;

    @JsonProperty("isRetryable")
    private Boolean isRetryable;

    private Instant attemptedAt;

    private String channel;

    private String provider;

    private String recipient;
}
