package com.pulserelay.service;

import com.pulserelay.dto.TenantRegistrationRequest;
import com.pulserelay.entity.Tenant;
import com.pulserelay.exception.TenantException;
import com.pulserelay.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing tenants.
 * Handles tenant registration, validation, and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    /**
     * Register a new tenant.
     * 
     * @param request Tenant registration details
     * @return The created tenant
     * @throws TenantException if organization name or email already exists
     */
    @Transactional
    public Tenant registerTenant(TenantRegistrationRequest request) {
        // Validate that organization name is unique
        if (tenantRepository.existsByOrganizationName(request.getOrganizationName())) {
            throw new TenantException("Organization name '" + request.getOrganizationName() + "' already exists");
        }

        // Validate that email is unique
        if (tenantRepository.existsByContactEmail(request.getContactEmail())) {
            throw new TenantException("Email '" + request.getContactEmail() + "' is already registered");
        }

        // Create and save the tenant
        Tenant tenant = Tenant.builder()
            .organizationName(request.getOrganizationName())
            .contactEmail(request.getContactEmail())
            .status(Tenant.TenantStatus.ACTIVE)
            .build();

        tenant = tenantRepository.save(tenant);
        log.info("Tenant registered: {} (ID: {})", request.getOrganizationName(), tenant.getId());

        return tenant;
    }

    /**
     * Get a tenant by ID.
     * 
     * @param tenantId The tenant ID
     * @return The tenant if found
     * @throws TenantException if tenant not found
     */
    @Transactional(readOnly = true)
    public Tenant getTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantException("Tenant not found: " + tenantId));
    }

    /**
     * Suspend a tenant.
     * 
     * @param tenantId The tenant ID
     */
    @Transactional
    public void suspendTenant(UUID tenantId) {
        Tenant tenant = getTenantById(tenantId);
        tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);
        log.warn("Tenant suspended: {}", tenantId);
    }

    /**
     * Activate a tenant.
     * 
     * @param tenantId The tenant ID
     */
    @Transactional
    public void activateTenant(UUID tenantId) {
        Tenant tenant = getTenantById(tenantId);
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        tenantRepository.save(tenant);
        log.info("Tenant activated: {}", tenantId);
    }
}
