package com.pulserelay.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for rendering a template with variables - Phase 08
 * Contains template ID and the payload data for variable substitution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateRenderRequest {

    @NotNull(message = "Template ID is required")
    private UUID templateId;

    @NotNull(message = "Payload variables are required")
    private Map<String, Object> payload;
}
