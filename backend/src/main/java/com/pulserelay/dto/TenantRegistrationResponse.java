package com.pulserelay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRegistrationResponse {

    private UUID tenantId;
    private String organizationName;
    private String contactEmail;
    private String status;
    private LocalDateTime createdAt;

    /**
     * The API key is returned ONLY ONCE during creation.
     * The client must store this securely.
     */
    private String apiKey;

    /**
     * Message informing the client that the API key will not be shown again.
     */
    private String apiKeyWarning;

    public static TenantRegistrationResponse from(UUID tenantId, String organizationName, 
                                                    String contactEmail, String status,
                                                    LocalDateTime createdAt, String apiKey) {
        return TenantRegistrationResponse.builder()
            .tenantId(tenantId)
            .organizationName(organizationName)
            .contactEmail(contactEmail)
            .status(status)
            .createdAt(createdAt)
            .apiKey(apiKey)
            .apiKeyWarning("⚠️ Save your API key securely. It will not be shown again. " +
                          "If lost, revoke this key and generate a new one.")
            .build();
    }
}
