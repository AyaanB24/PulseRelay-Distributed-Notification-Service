package com.pulserelay.controller;

import com.pulserelay.dto.TenantRegistrationRequest;
import com.pulserelay.dto.TenantRegistrationResponse;
import com.pulserelay.entity.Tenant;
import com.pulserelay.service.ApiKeyService;
import com.pulserelay.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin endpoints for tenant management.
 * All endpoints in this controller require admin authentication via X-ADMIN-SECRET header.
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class TenantAdminController {

    private final TenantService tenantService;
    private final ApiKeyService apiKeyService;

    /**
     * Admin secret from environment configuration.
     * Must be set in application.yml as pulserelay.admin.secret
     */
    @Value("${pulserelay.admin.secret:#{null}}")
    private String adminSecret;

    /**
     * Register a new tenant and generate an API key.
     * 
     * Endpoint: POST /api/v1/admin/tenants
     * 
     * Headers:
     *   X-ADMIN-SECRET: The admin secret (required)
     * 
     * Request body:
     * {
     *   "organizationName": "Flipkart",
     *   "contactEmail": "admin@flipkart.com"
     * }
     * 
     * Response (201 Created):
     * {
     *   "tenantId": "550e8400-e29b-41d4-a716-446655440000",
     *   "organizationName": "Flipkart",
     *   "contactEmail": "admin@flipkart.com",
     *   "status": "ACTIVE",
     *   "createdAt": "2026-11-04T12:34:56",
     *   "apiKey": "pr_live_xxxxxxxxxxx",
     *   "apiKeyWarning": "⚠️ Save your API key securely. It will not be shown again..."
     * }
     */
    @PostMapping("/tenants")
    public ResponseEntity<TenantRegistrationResponse> registerTenant(
            @RequestHeader(value = "X-ADMIN-SECRET", required = false) String providedSecret,
            @Valid @RequestBody TenantRegistrationRequest request) {

        // Validate admin secret
        if (!validateAdminSecret(providedSecret)) {
            log.warn("Invalid admin secret provided for tenant registration");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(null);
        }

        // Register the tenant
        Tenant tenant = tenantService.registerTenant(request);

        // Generate an API key for the tenant
        String apiKey = apiKeyService.createApiKey(tenant, "default");

        // Build response (API key shown only once)
        TenantRegistrationResponse response = TenantRegistrationResponse.from(
            tenant.getId(),
            tenant.getOrganizationName(),
            tenant.getContactEmail(),
            tenant.getStatus().toString(),
            tenant.getCreatedAt(),
            apiKey
        );

        log.info("Tenant registered successfully: {} (ID: {})", tenant.getOrganizationName(), tenant.getId());

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/v1/admin/tenants/" + tenant.getId())
            .body(response);
    }

    /**
     * Validate the admin secret from the request header.
     * 
     * @param providedSecret The secret from X-ADMIN-SECRET header
     * @return true if valid, false otherwise
     */
    private boolean validateAdminSecret(String providedSecret) {
        // Admin secret must be configured
        if (adminSecret == null || adminSecret.isBlank()) {
            log.error("Admin secret is not configured. Set 'pulserelay.admin.secret' in application.yml");
            return false;
        }

        // Admin secret must match exactly
        if (providedSecret == null || providedSecret.isBlank()) {
            return false;
        }

        // Use constant-time comparison to prevent timing attacks
        return constantTimeEquals(providedSecret, adminSecret);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();

        int diff = aBytes.length ^ bBytes.length;
        for (int i = 0; i < Math.min(aBytes.length, bBytes.length); i++) {
            diff |= aBytes[i] ^ bBytes[i];
        }

        return diff == 0;
    }
}
