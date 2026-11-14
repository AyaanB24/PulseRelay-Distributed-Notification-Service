package com.pulserelay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating/updating notification templates - Phase 08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateRequest {

    @NotBlank(message = "Template name is required")
    private String name;

    @NotBlank(message = "Event type is required")
    private String eventType;

    @NotBlank(message = "Channel is required (EMAIL, SMS, PUSH, IN_APP)")
    private String channel;

    // Subject is optional for SMS/PUSH, required for EMAIL
    // Validation is done in service layer
    private String subject;

    @NotBlank(message = "Template body is required")
    private String body;

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;
}
