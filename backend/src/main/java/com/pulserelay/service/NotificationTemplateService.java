package com.pulserelay.service;

import com.pulserelay.dto.NotificationTemplateRequest;
import com.pulserelay.dto.NotificationTemplateResponse;
import com.pulserelay.dto.TemplateRenderRequest;
import com.pulserelay.dto.TemplateRenderResponse;
import com.pulserelay.entity.NotificationTemplate;
import com.pulserelay.entity.Tenant;
import com.pulserelay.exception.NotificationException;
import com.pulserelay.exception.TenantException;
import com.pulserelay.repository.NotificationTemplateRepository;
import com.pulserelay.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing notification templates and rendering - Phase 08
 * Handles template CRUD operations, rendering with variable substitution,
 * and tenant isolation.
 */
@Service
@Slf4j
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final TenantRepository tenantRepository;

    // Pattern to match placeholders: {{variableName}}
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}");

    // Valid channels
    private static final Set<String> VALID_CHANNELS = Set.of("EMAIL", "SMS", "PUSH", "IN_APP");

    @Autowired
    public NotificationTemplateService(
            NotificationTemplateRepository templateRepository,
            TenantRepository tenantRepository) {
        this.templateRepository = templateRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Create a new notification template
     */
    @Transactional
    public NotificationTemplateResponse createTemplate(UUID tenantId, NotificationTemplateRequest request) {
        log.info("Creating template for tenant: {}, eventType: {}, channel: {}", 
                tenantId, request.getEventType(), request.getChannel());

        // Verify tenant exists
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantException("Tenant not found: " + tenantId));

        // Validate channel
        validateChannel(request.getChannel());

        // Validate subject for EMAIL channel
        if ("EMAIL".equals(request.getChannel()) && 
                (request.getSubject() == null || request.getSubject().isBlank())) {
            throw new NotificationException("Subject is required for EMAIL templates");
        }

        // Check for duplicate template (same tenant, event type, channel)
        Optional<NotificationTemplate> existingTemplate = 
                templateRepository.findByTenantIdAndEventTypeAndChannel(
                        tenantId, request.getEventType(), request.getChannel());

        if (existingTemplate.isPresent()) {
            throw new NotificationException(
                    String.format("Template already exists for tenant, eventType=%s, channel=%s",
                            request.getEventType(), request.getChannel()));
        }

        // Create template
        NotificationTemplate template = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name(request.getName())
                .eventType(request.getEventType())
                .channel(request.getChannel())
                .subject(request.getSubject())
                .body(request.getBody())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        template = templateRepository.save(template);
        log.info("Template created successfully with ID: {}", template.getId());

        return toResponse(template);
    }

    /**
     * List all templates for a tenant
     */
    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> listTemplates(UUID tenantId) {
        log.info("Listing templates for tenant: {}", tenantId);

        List<NotificationTemplate> templates = templateRepository.findByTenantId(tenantId);
        return templates.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get a specific template
     */
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getTemplate(UUID tenantId, UUID templateId) {
        log.info("Retrieving template: {} for tenant: {}", templateId, tenantId);

        NotificationTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NotificationException(
                        "Template not found or access denied: " + templateId));

        return toResponse(template);
    }

    /**
     * Update a template
     */
    @Transactional
    public NotificationTemplateResponse updateTemplate(
            UUID tenantId, UUID templateId, NotificationTemplateRequest request) {
        log.info("Updating template: {} for tenant: {}", templateId, tenantId);

        NotificationTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NotificationException(
                        "Template not found or access denied: " + templateId));

        // Validate channel
        validateChannel(request.getChannel());

        // Validate subject for EMAIL channel
        if ("EMAIL".equals(request.getChannel()) && 
                (request.getSubject() == null || request.getSubject().isBlank())) {
            throw new NotificationException("Subject is required for EMAIL templates");
        }

        // Check for duplicate if event type or channel changed
        if (!template.getEventType().equals(request.getEventType()) || 
                !template.getChannel().equals(request.getChannel())) {
            Optional<NotificationTemplate> existingTemplate = 
                    templateRepository.findByTenantIdAndEventTypeAndChannel(
                            tenantId, request.getEventType(), request.getChannel());

            if (existingTemplate.isPresent() && !existingTemplate.get().getId().equals(templateId)) {
                throw new NotificationException(
                        String.format("Template already exists for tenant, eventType=%s, channel=%s",
                                request.getEventType(), request.getChannel()));
            }
        }

        // Update fields
        template.setName(request.getName());
        template.setEventType(request.getEventType());
        template.setChannel(request.getChannel());
        template.setSubject(request.getSubject());
        template.setBody(request.getBody());
        template.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        template = templateRepository.save(template);
        log.info("Template updated successfully: {}", templateId);

        return toResponse(template);
    }

    /**
     * Toggle template enabled/disabled state
     */
    @Transactional
    public NotificationTemplateResponse toggleTemplate(UUID tenantId, UUID templateId, boolean enabled) {
        log.info("Toggling template: {} enabled={} for tenant: {}", templateId, enabled, tenantId);

        NotificationTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NotificationException(
                        "Template not found or access denied: " + templateId));

        template.setEnabled(enabled);
        template = templateRepository.save(template);

        return toResponse(template);
    }

    /**
     * Delete a template
     */
    @Transactional
    public void deleteTemplate(UUID tenantId, UUID templateId) {
        log.info("Deleting template: {} for tenant: {}", templateId, tenantId);

        NotificationTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NotificationException(
                        "Template not found or access denied: " + templateId));

        templateRepository.delete(template);
        log.info("Template deleted successfully: {}", templateId);
    }

    /**
     * Render a template with provided payload variables
     * Performs safe variable substitution without expression evaluation
     */
    @Transactional(readOnly = true)
    public TemplateRenderResponse renderTemplate(UUID tenantId, TemplateRenderRequest request) {
        log.info("Rendering template: {} for tenant: {}", request.getTemplateId(), tenantId);

        // Get template (with tenant isolation)
        NotificationTemplate template = templateRepository.findByIdAndTenantId(request.getTemplateId(), tenantId)
                .orElseThrow(() -> new NotificationException(
                        "Template not found or access denied: " + request.getTemplateId()));

        if (!template.getEnabled()) {
            throw new NotificationException(
                    "Template is disabled: " + request.getTemplateId());
        }

        // Render subject and body
        String renderedSubject = renderContent(template.getSubject(), request.getPayload());
        String renderedBody = renderContent(template.getBody(), request.getPayload());

        log.info("Template rendered successfully");

        return TemplateRenderResponse.builder()
                .subject(renderedSubject)
                .body(renderedBody)
                .channel(template.getChannel())
                .build();
    }

    /**
     * Internal method: Render content by replacing placeholders with payload values
     * Uses safe regex-based substitution (no expression evaluation)
     * Throws exception if required variable is missing
     */
    private String renderContent(String template, Map<String, Object> payload) {
        if (template == null) {
            return null;
        }

        StringBuffer sb = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = payload.get(variableName);

            if (value == null) {
                throw new NotificationException(
                        "Missing required variable in payload: " + variableName);
            }

            // Convert value to string safely
            String stringValue = convertToString(value);
            // Escape special regex characters in replacement
            matcher.appendReplacement(sb, Matcher.quoteReplacement(stringValue));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Safely convert values to strings
     */
    private String convertToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        // For complex types, convert to string (not JSON)
        return value.toString();
    }

    /**
     * Validate channel is one of the supported channels
     */
    private void validateChannel(String channel) {
        if (channel == null || !VALID_CHANNELS.contains(channel.toUpperCase())) {
            throw new NotificationException(
                    "Invalid channel: " + channel + ". Valid channels are: EMAIL, SMS, PUSH, IN_APP");
        }
    }

    /**
     * Convert entity to response DTO
     */
    private NotificationTemplateResponse toResponse(NotificationTemplate template) {
        return NotificationTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .eventType(template.getEventType())
                .channel(template.getChannel())
                .subject(template.getSubject())
                .body(template.getBody())
                .enabled(template.getEnabled())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
