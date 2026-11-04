package com.pulserelay.repository;

import com.pulserelay.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Find a tenant by organization name.
     */
    Optional<Tenant> findByOrganizationName(String organizationName);

    /**
     * Find a tenant by contact email.
     */
    Optional<Tenant> findByContactEmail(String contactEmail);

    /**
     * Check if a tenant exists by organization name.
     */
    boolean existsByOrganizationName(String organizationName);

    /**
     * Check if a tenant exists by contact email.
     */
    boolean existsByContactEmail(String contactEmail);
}
