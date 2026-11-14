package com.pulserelay.dto;

import com.pulserelay.entity.RoutingPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for routing policy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingPolicyResponse {

    /**
     * Policy UUID
     */
    private UUID id;

    /**
     * Event type this policy applies to
     */
    private String eventType;

    /**
     * Primary notification channels
     */
    private String primaryChannels;

    /**
     * Fallback channels
     */
    private String fallbackChannels;

    /**
     * Priority level
     */
    private String priority;

    /**
     * Whether this policy is active
     */
    private Boolean enabled;

    /**
     * Timestamp when this policy was created
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when this policy was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * Convert RoutingPolicy entity to response DTO
     */
    public static RoutingPolicyResponse from(RoutingPolicy policy) {
        return RoutingPolicyResponse.builder()
                .id(policy.getId())
                .eventType(policy.getEventType())
                .primaryChannels(policy.getPrimaryChannels())
                .fallbackChannels(policy.getFallbackChannels())
                .priority(policy.getPriority())
                .enabled(policy.getEnabled())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
