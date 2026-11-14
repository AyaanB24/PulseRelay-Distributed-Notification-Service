package com.pulserelay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for notification templates - Phase 08
 * Contains template metadata and content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateResponse {

    private UUID id;

    private String name;

    private String eventType;

    private String channel;

    private String subject;

    private String body;

    private Boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;
}
