package com.pulserelay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for rendered template output - Phase 08
 * Contains the rendered subject and body after variable substitution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateRenderResponse {

    private String subject;

    private String body;

    private String channel;
}
