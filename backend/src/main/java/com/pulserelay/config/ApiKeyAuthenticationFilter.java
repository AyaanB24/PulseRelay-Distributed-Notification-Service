package com.pulserelay.config;

import com.pulserelay.entity.ApiKey;
import com.pulserelay.exception.ApiKeyException;
import com.pulserelay.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authentication filter for API key-based authentication.
 * Reads the API key from the X-API-KEY header and validates it.
 * 
 * Public endpoints (without context path) that skip authentication:
 * - /actuator/health
 * - /actuator/info
 * - /v1/admin/tenants (admin registration with X-ADMIN-SECRET)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

    /**
     * Public paths that do NOT require X-API-KEY authentication.
     * These are relative to the context path (without /api prefix).
     * Admin registration uses X-ADMIN-SECRET instead of X-API-KEY.
     */
    private static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/actuator/info",
        "/v1/admin/tenants"
    };

    private final ApiKeyService apiKeyService;

    /**
     * Skip API key authentication for public endpoints.
     * 
     * Uses getServletPath() which returns the path WITHOUT context path.
     * For example, if context path is /api:
     * - Full URL: http://localhost:8084/api/actuator/health
     * - getServletPath(): /actuator/health
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath();

        for (String publicPath : PUBLIC_PATHS) {
            if (servletPath.equals(publicPath)) {
                log.debug("Skipping authentication for public path: {}", servletPath);
                return true;
            }
        }

        return false;
    }

    /**
     * Authenticate tenant requests using X-API-KEY.
     * This method only runs for protected endpoints (shouldNotFilter returns false).
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Extract API key from header
            String apiKey = request.getHeader(API_KEY_HEADER);

            if (apiKey == null || apiKey.isBlank()) {
                sendUnauthorizedResponse(response, "API key is required");
                return;
            }

            // Validate API key and retrieve associated tenant
            ApiKey authenticatedKey = apiKeyService.authenticateApiKey(apiKey);

            // Create tenant principal
            TenantPrincipal principal = TenantPrincipal.from(authenticatedKey.getTenant());

            // Create Spring Security authentication token
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            // Store authentication in SecurityContext for downstream handlers
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("API key authenticated for tenant: {}", principal.getTenantId());

            // Continue filter chain with authenticated context
            filterChain.doFilter(request, response);

        } catch (ApiKeyException exception) {
            SecurityContextHolder.clearContext();
            log.debug("API key authentication failed: {}", exception.getMessage());
            sendUnauthorizedResponse(response, "Invalid or revoked API key");

        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            log.error("Unexpected error during API key authentication", exception);
            
            if (!response.isCommitted()) {
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Authentication processing failed"
                );
            }
        }
    }

    /**
     * Send a consistent JSON 401 Unauthorized response.
     */
    private void sendUnauthorizedResponse(
            HttpServletResponse response,
            String message
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"" + message + "\",\"status\":401}"
        );
    }
}
