package com.pulserelay.controller;

import com.pulserelay.config.TenantPrincipal;
import com.pulserelay.dto.TenantInfoResponse;
import com.pulserelay.entity.ApiKey;
import com.pulserelay.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant endpoints for authenticated tenant operations.
 * All endpoints require X-API-KEY authentication.
 * Tenant information is extracted from the authenticated security context.
 */
@RestController
@RequestMapping("/v1/tenant")
@RequiredArgsConstructor
@Slf4j
public class TenantController {

    private final ApiKeyService apiKeyService;

    /**
     * Get authenticated tenant information.
     * 
     * Endpoint: GET /api/v1/tenant/me
     * 
     * Headers:
     *   X-API-KEY: The tenant's API key (required)
     * 
     * Response (200 OK):
     * {
     *   "tenantId": "550e8400-e29b-41d4-a716-446655440000",
     *   "organizationName": "Flipkart",
     *   "contactEmail": "admin@flipkart.com",
     *   "status": "ACTIVE"
     * }
     * 
     * Response (401 Unauthorized):
     * - Missing X-API-KEY header
     * - Invalid or revoked API key
     * - Associated tenant is SUSPENDED
     * 
     * @return TenantInfoResponse with authenticated tenant details
     */
    @GetMapping("/me")
    public ResponseEntity<TenantInfoResponse> getTenantInfo() {
        try {
            // Extract authentication from SecurityContext
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("No authenticated principal found in security context");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get the authenticated tenant principal
            Object principal = authentication.getPrincipal();

            if (!(principal instanceof TenantPrincipal)) {
                log.error("Principal is not a TenantPrincipal: {}", principal.getClass().getName());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            TenantPrincipal tenantPrincipal = (TenantPrincipal) principal;

            // Retrieve the ApiKey entity to get the current tenant status
            // The tenant status is verified during API key authentication in ApiKeyAuthenticationFilter,
            // but we fetch it here to ensure the status is current
            String currentStatus = apiKeyService.getTenantStatusByPrincipal(tenantPrincipal);

            // Build response (no API key exposed)
            TenantInfoResponse response = TenantInfoResponse.from(tenantPrincipal, currentStatus);

            log.debug("Tenant information retrieved for tenant: {}", tenantPrincipal.getTenantId());

            return ResponseEntity.ok(response);

        } catch (Exception exception) {
            log.error("Unexpected error while retrieving tenant information", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
