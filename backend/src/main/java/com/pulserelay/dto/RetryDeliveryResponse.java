package com.pulserelay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * RetryDeliveryResponse DTO - Phase 10
 * Response from delivery endpoint showing retry status
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class RetryDeliveryResponse {

    private UUID id;

    private String status;  // ACCEPTED, DELIVERED, FAILED, PERMANENT_FAILURE, RETRY_SCHEDULED

    private String errorCode;

    private String errorMessage;

    private String providerMessageId;

    @JsonProperty("isRetryable")
    private Boolean isRetryable;

    private Instant attemptedAt;

    private Instant nextAttemptAt;  // When retry is scheduled (null if not retrying)

    private String channel;

    private String provider;

    private String recipient;
}
