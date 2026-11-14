package com.pulserelay.controller;

import com.pulserelay.config.TenantPrincipal;
import com.pulserelay.dto.RoutingPolicyRequest;
import com.pulserelay.dto.RoutingPolicyResponse;
import com.pulserelay.service.RoutingPolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * API Controller for Phase 07: Configurable Notification Routing Policies.
 * Provides tenant-authenticated endpoints for managing routing policies.
 */
@RestController
@RequestMapping("/v1/routing-policies")
@Slf4j
public class RoutingPolicyController {

    private final RoutingPolicyService routingPolicyService;

    @Autowired
    public RoutingPolicyController(RoutingPolicyService routingPolicyService) {
        this.routingPolicyService = routingPolicyService;
    }

    /**
     * Create a new routing policy
     * POST /api/v1/routing-policies
     */
    @PostMapping
    public ResponseEntity<RoutingPolicyResponse> createPolicy(
            Authentication authentication,
            @Valid @RequestBody RoutingPolicyRequest request) {
        log.info("POST /routing-policies - Creating policy for event type: {}", request.getEventType());

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        RoutingPolicyResponse response = routingPolicyService.createPolicy(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all routing policies for the tenant
     * GET /api/v1/routing-policies
     */
    @GetMapping
    public ResponseEntity<List<RoutingPolicyResponse>> listPolicies(Authentication authentication) {
        log.info("GET /routing-policies - Listing policies");

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        List<RoutingPolicyResponse> policies = routingPolicyService.listPolicies(tenantId);
        return ResponseEntity.ok(policies);
    }

    /**
     * Retrieve a specific routing policy
     * GET /api/v1/routing-policies/{policyId}
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<RoutingPolicyResponse> getPolicy(
            Authentication authentication,
            @PathVariable UUID policyId) {
        log.info("GET /routing-policies/{} - Retrieving policy", policyId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        RoutingPolicyResponse response = routingPolicyService.getPolicy(tenantId, policyId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update a routing policy
     * PUT /api/v1/routing-policies/{policyId}
     */
    @PutMapping("/{policyId}")
    public ResponseEntity<RoutingPolicyResponse> updatePolicy(
            Authentication authentication,
            @PathVariable UUID policyId,
            @Valid @RequestBody RoutingPolicyRequest request) {
        log.info("PUT /routing-policies/{} - Updating policy", policyId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        RoutingPolicyResponse response = routingPolicyService.updatePolicy(tenantId, policyId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Enable a routing policy
     * PATCH /api/v1/routing-policies/{policyId}/enable
     */
    @PatchMapping("/{policyId}/enable")
    public ResponseEntity<RoutingPolicyResponse> enablePolicy(
            Authentication authentication,
            @PathVariable UUID policyId) {
        log.info("PATCH /routing-policies/{}/enable - Enabling policy", policyId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        RoutingPolicyResponse response = routingPolicyService.togglePolicy(tenantId, policyId, true);
        return ResponseEntity.ok(response);
    }

    /**
     * Disable a routing policy
     * PATCH /api/v1/routing-policies/{policyId}/disable
     */
    @PatchMapping("/{policyId}/disable")
    public ResponseEntity<RoutingPolicyResponse> disablePolicy(
            Authentication authentication,
            @PathVariable UUID policyId) {
        log.info("PATCH /routing-policies/{}/disable - Disabling policy", policyId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        RoutingPolicyResponse response = routingPolicyService.togglePolicy(tenantId, policyId, false);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a routing policy
     * DELETE /api/v1/routing-policies/{policyId}
     */
    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> deletePolicy(
            Authentication authentication,
            @PathVariable UUID policyId) {
        log.info("DELETE /routing-policies/{} - Deleting policy", policyId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        routingPolicyService.deletePolicy(tenantId, policyId);
        return ResponseEntity.noContent().build();
    }
}
