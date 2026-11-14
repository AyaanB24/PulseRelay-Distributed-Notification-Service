package com.pulserelay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * TestDeliveryRequest DTO - Phase 09
 * Used for testing notification delivery through providers
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TestDeliveryRequest {

    @NotNull(message = "channel is required")
    @NotBlank(message = "channel cannot be blank")
    private String channel;  // EMAIL, SMS, PUSH, IN_APP

    @NotNull(message = "recipient is required")
    @NotBlank(message = "recipient cannot be blank")
    private String recipient;  // Email, phone, device token, etc.

    private String subject;  // For EMAIL channel

    @NotNull(message = "body is required")
    @NotBlank(message = "body cannot be blank")
    private String body;
}
