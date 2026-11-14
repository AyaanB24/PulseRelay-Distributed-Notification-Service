package com.pulserelay.controller;

import com.pulserelay.config.TenantPrincipal;
import com.pulserelay.dto.NotificationTemplateRequest;
import com.pulserelay.dto.NotificationTemplateResponse;
import com.pulserelay.dto.TemplateRenderRequest;
import com.pulserelay.dto.TemplateRenderResponse;
import com.pulserelay.service.NotificationTemplateService;
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
 * API Controller for Phase 08: Notification Template Management and Rendering.
 * Provides tenant-authenticated endpoints for managing and rendering templates.
 */
@RestController
@RequestMapping("/v1/notification-templates")
@Slf4j
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    @Autowired
    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Create a new notification template
     * POST /api/v1/notification-templates
     */
    @PostMapping
    public ResponseEntity<NotificationTemplateResponse> createTemplate(
            Authentication authentication,
            @Valid @RequestBody NotificationTemplateRequest request) {
        log.info("POST /notification-templates - Creating template for event: {}, channel: {}", 
                request.getEventType(), request.getChannel());

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        NotificationTemplateResponse response = templateService.createTemplate(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all templates for the tenant
     * GET /api/v1/notification-templates
     */
    @GetMapping
    public ResponseEntity<List<NotificationTemplateResponse>> listTemplates(Authentication authentication) {
        log.info("GET /notification-templates - Listing templates");

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        List<NotificationTemplateResponse> templates = templateService.listTemplates(tenantId);
        return ResponseEntity.ok(templates);
    }

    /**
     * Retrieve a specific template
     * GET /api/v1/notification-templates/{templateId}
     */
    @GetMapping("/{templateId}")
    public ResponseEntity<NotificationTemplateResponse> getTemplate(
            Authentication authentication,
            @PathVariable UUID templateId) {
        log.info("GET /notification-templates/{} - Retrieving template", templateId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        NotificationTemplateResponse response = templateService.getTemplate(tenantId, templateId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update a template
     * PUT /api/v1/notification-templates/{templateId}
     */
    @PutMapping("/{templateId}")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            Authentication authentication,
            @PathVariable UUID templateId,
            @Valid @RequestBody NotificationTemplateRequest request) {
        log.info("PUT /notification-templates/{} - Updating template", templateId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        NotificationTemplateResponse response = templateService.updateTemplate(tenantId, templateId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Enable a template
     * PATCH /api/v1/notification-templates/{templateId}/enable
     */
    @PatchMapping("/{templateId}/enable")
    public ResponseEntity<NotificationTemplateResponse> enableTemplate(
            Authentication authentication,
            @PathVariable UUID templateId) {
        log.info("PATCH /notification-templates/{}/enable - Enabling template", templateId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        NotificationTemplateResponse response = templateService.toggleTemplate(tenantId, templateId, true);
        return ResponseEntity.ok(response);
    }

    /**
     * Disable a template
     * PATCH /api/v1/notification-templates/{templateId}/disable
     */
    @PatchMapping("/{templateId}/disable")
    public ResponseEntity<NotificationTemplateResponse> disableTemplate(
            Authentication authentication,
            @PathVariable UUID templateId) {
        log.info("PATCH /notification-templates/{}/disable - Disabling template", templateId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        NotificationTemplateResponse response = templateService.toggleTemplate(tenantId, templateId, false);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a template
     * DELETE /api/v1/notification-templates/{templateId}
     */
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(
            Authentication authentication,
            @PathVariable UUID templateId) {
        log.info("DELETE /notification-templates/{} - Deleting template", templateId);

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        templateService.deleteTemplate(tenantId, templateId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Render a template with provided variables
     * POST /api/v1/notification-templates/render
     * Note: Using /render suffix to avoid conflict with POST {id}
     */
    @PostMapping("/render")
    public ResponseEntity<TemplateRenderResponse> renderTemplate(
            Authentication authentication,
            @Valid @RequestBody TemplateRenderRequest request) {
        log.info("POST /notification-templates/render - Rendering template: {}", request.getTemplateId());

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.getTenantId();

        TemplateRenderResponse response = templateService.renderTemplate(tenantId, request);
        return ResponseEntity.ok(response);
    }
}
