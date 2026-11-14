package com.pulserelay.service;

import com.pulserelay.dto.RoutingPolicyRequest;
import com.pulserelay.dto.RoutingPolicyResponse;
import com.pulserelay.entity.RoutingPolicy;
import com.pulserelay.entity.Tenant;
import com.pulserelay.exception.NotificationException;
import com.pulserelay.exception.TenantException;
import com.pulserelay.repository.RoutingPolicyRepository;
import com.pulserelay.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing routing policies.
 * Handles CRUD operations, validation, and policy resolution.
 */
@Service
@Slf4j
@Transactional
public class RoutingPolicyService {

    private final RoutingPolicyRepository routingPolicyRepository;
    private final TenantRepository tenantRepository;

    private static final String[] VALID_CHANNELS = {"EMAIL", "SMS", "PUSH", "IN_APP"};
    private static final String[] VALID_PRIORITIES = {"CRITICAL", "HIGH", "NORMAL", "LOW"};

    @Autowired
    public RoutingPolicyService(RoutingPolicyRepository routingPolicyRepository, TenantRepository tenantRepository) {
        this.routingPolicyRepository = routingPolicyRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Create a new routing policy for a tenant
     */
    public RoutingPolicyResponse createPolicy(UUID tenantId, RoutingPolicyRequest request) {
        log.info("Creating routing policy for tenant: {}, eventType: {}", tenantId, request.getEventType());

        // Verify tenant exists
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantException("Tenant not found: " + tenantId));

        // Validate channels
        validateChannels(request.getPrimaryChannels());
        if (request.getFallbackChannels() != null && !request.getFallbackChannels().isBlank()) {
            validateChannels(request.getFallbackChannels());
        }

        // Validate priority
        validatePriority(request.getPriority());

        // Check for duplicate policy
        if (routingPolicyRepository.existsByTenantIdAndEventType(tenantId, request.getEventType())) {
            throw new NotificationException("Policy already exists for this tenant and event type: " + request.getEventType());
        }

        // Create policy
        RoutingPolicy policy = RoutingPolicy.builder()
                .tenant(tenant)
                .eventType(request.getEventType())
                .primaryChannels(request.getPrimaryChannels())
                .fallbackChannels(request.getFallbackChannels())
                .priority(request.getPriority())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        policy = routingPolicyRepository.save(policy);
        log.info("Routing policy created: id={}, tenant={}, eventType={}", policy.getId(), tenantId, request.getEventType());

        return RoutingPolicyResponse.from(policy);
    }

    /**
     * Retrieve a specific routing policy
     */
    @Transactional(readOnly = true)
    public RoutingPolicyResponse getPolicy(UUID tenantId, UUID policyId) {
        log.info("Retrieving routing policy: id={}, tenant={}", policyId, tenantId);

        RoutingPolicy policy = routingPolicyRepository.findById(policyId)
                .orElseThrow(() -> new NotificationException("Policy not found: " + policyId));

        // Enforce tenant isolation
        if (!policy.getTenant().getId().equals(tenantId)) {
            throw new NotificationException("Access denied: Policy does not belong to this tenant");
        }

        return RoutingPolicyResponse.from(policy);
    }

    /**
     * List all routing policies for a tenant
     */
    @Transactional(readOnly = true)
    public List<RoutingPolicyResponse> listPolicies(UUID tenantId) {
        log.info("Listing routing policies for tenant: {}", tenantId);

        // Verify tenant exists
        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantException("Tenant not found: " + tenantId);
        }

        List<RoutingPolicy> policies = routingPolicyRepository.findByTenantId(tenantId);
        return policies.stream()
                .map(RoutingPolicyResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Update a routing policy
     */
    public RoutingPolicyResponse updatePolicy(UUID tenantId, UUID policyId, RoutingPolicyRequest request) {
        log.info("Updating routing policy: id={}, tenant={}", policyId, tenantId);

        RoutingPolicy policy = routingPolicyRepository.findById(policyId)
                .orElseThrow(() -> new NotificationException("Policy not found: " + policyId));

        // Enforce tenant isolation
        if (!policy.getTenant().getId().equals(tenantId)) {
            throw new NotificationException("Access denied: Policy does not belong to this tenant");
        }

        // Validate channels
        validateChannels(request.getPrimaryChannels());
        if (request.getFallbackChannels() != null && !request.getFallbackChannels().isBlank()) {
            validateChannels(request.getFallbackChannels());
        }

        // Validate priority
        validatePriority(request.getPriority());

        // Check for duplicate if event type changed
        if (!policy.getEventType().equals(request.getEventType())) {
            if (routingPolicyRepository.existsByTenantIdAndEventType(tenantId, request.getEventType())) {
                throw new NotificationException("Policy already exists for this tenant and event type: " + request.getEventType());
            }
        }

        // Update fields
        policy.setEventType(request.getEventType());
        policy.setPrimaryChannels(request.getPrimaryChannels());
        policy.setFallbackChannels(request.getFallbackChannels());
        policy.setPriority(request.getPriority());
        if (request.getEnabled() != null) {
            policy.setEnabled(request.getEnabled());
        }

        policy = routingPolicyRepository.save(policy);
        log.info("Routing policy updated: id={}, eventType={}", policy.getId(), policy.getEventType());

        return RoutingPolicyResponse.from(policy);
    }

    /**
     * Enable or disable a routing policy
     */
    public RoutingPolicyResponse togglePolicy(UUID tenantId, UUID policyId, boolean enabled) {
        log.info("Toggling routing policy: id={}, tenant={}, enabled={}", policyId, tenantId, enabled);

        RoutingPolicy policy = routingPolicyRepository.findById(policyId)
                .orElseThrow(() -> new NotificationException("Policy not found: " + policyId));

        // Enforce tenant isolation
        if (!policy.getTenant().getId().equals(tenantId)) {
            throw new NotificationException("Access denied: Policy does not belong to this tenant");
        }

        policy.setEnabled(enabled);
        policy = routingPolicyRepository.save(policy);

        log.info("Routing policy toggled: id={}, enabled={}", policy.getId(), policy.getEnabled());
        return RoutingPolicyResponse.from(policy);
    }

    /**
     * Delete a routing policy
     */
    public void deletePolicy(UUID tenantId, UUID policyId) {
        log.info("Deleting routing policy: id={}, tenant={}", policyId, tenantId);

        RoutingPolicy policy = routingPolicyRepository.findById(policyId)
                .orElseThrow(() -> new NotificationException("Policy not found: " + policyId));

        // Enforce tenant isolation
        if (!policy.getTenant().getId().equals(tenantId)) {
            throw new NotificationException("Access denied: Policy does not belong to this tenant");
        }

        routingPolicyRepository.deleteById(policyId);
        log.info("Routing policy deleted: id={}", policyId);
    }

    /**
     * Resolve routing policy for a notification (Phase 07 core logic)
     * Returns the policy or throws if not found/disabled
     */
    @Transactional(readOnly = true)
    public RoutingPolicy resolvePolicy(UUID tenantId, String eventType) {
        log.debug("Resolving routing policy for tenant: {}, eventType: {}", tenantId, eventType);

        RoutingPolicy policy = routingPolicyRepository.findByTenantIdAndEventType(tenantId, eventType)
                .orElseThrow(() -> new NotificationException(
                    "No routing policy found for tenant: " + tenantId + ", eventType: " + eventType
                ));

        // Check if policy is enabled
        if (!policy.getEnabled()) {
            throw new NotificationException(
                "Routing policy is disabled for tenant: " + tenantId + ", eventType: " + eventType
            );
        }

        return policy;
    }

    /**
     * Validate that all channels are valid
     */
    private void validateChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            throw new NotificationException("Channels cannot be empty");
        }

        String[] channelArray = channels.split(",");
        for (String channel : channelArray) {
            channel = channel.trim();
            boolean valid = false;
            for (String validChannel : VALID_CHANNELS) {
                if (validChannel.equals(channel)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                throw new NotificationException("Invalid channel: " + channel + ". Valid channels are: EMAIL, SMS, PUSH, IN_APP");
            }
        }
    }

    /**
     * Validate priority
     */
    private void validatePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            throw new NotificationException("Priority cannot be empty");
        }

        for (String validPriority : VALID_PRIORITIES) {
            if (validPriority.equals(priority)) {
                return;
            }
        }
        throw new NotificationException("Invalid priority: " + priority + ". Valid priorities are: CRITICAL, HIGH, NORMAL, LOW");
    }
}
