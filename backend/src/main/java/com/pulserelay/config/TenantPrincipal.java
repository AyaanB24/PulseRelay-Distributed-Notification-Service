package com.pulserelay.config;

import com.pulserelay.entity.Tenant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Spring Security Principal representing an authenticated tenant.
 * Wraps tenant information and makes it available in the security context.
 */
@Getter
@AllArgsConstructor
public class TenantPrincipal implements UserDetails {

    private UUID tenantId;
    private String organizationName;
    private String contactEmail;

    /**
     * Create a TenantPrincipal from a Tenant entity.
     */
    public static TenantPrincipal from(Tenant tenant) {
        return new TenantPrincipal(
            tenant.getId(),
            tenant.getOrganizationName(),
            tenant.getContactEmail()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // All authenticated tenants have the ROLE_TENANT authority
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_TENANT"));
    }

    @Override
    public String getPassword() {
        // API key authentication doesn't use passwords
        return null;
    }

    @Override
    public String getUsername() {
        return tenantId.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
