package com.pulserelay.dto;

import com.pulserelay.config.TenantPrincipal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for authenticated tenant information.
 * Returned by GET /api/v1/tenant/me endpoint.
 * Does not expose API keys or sensitive security data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInfoResponse {

    private UUID tenantId;
    private String organizationName;
    private String contactEmail;
    private String status;

    /**
     * Create a TenantInfoResponse from an authenticated TenantPrincipal.
     * 
     * @param principal The authenticated tenant from SecurityContext
     * @param status The current tenant status (ACTIVE or SUSPENDED)
     * @return TenantInfoResponse with tenant details (no API key exposed)
     */
    public static TenantInfoResponse from(TenantPrincipal principal, String status) {
        return TenantInfoResponse.builder()
            .tenantId(principal.getTenantId())
            .organizationName(principal.getOrganizationName())
            .contactEmail(principal.getContactEmail())
            .status(status)
            .build();
    }
}
