package com.pulserelay.repository;

import com.pulserelay.entity.ApiKey;
import com.pulserelay.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find an API key by its SHA-256 hash.
     * Used during authentication.
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Find an active API key by its hash with tenant information.
     * Ensures the key is active and retrieves tenant details in one query.
     */
    @Query("SELECT ak FROM ApiKey ak JOIN FETCH ak.tenant " +
           "WHERE ak.keyHash = :keyHash AND ak.status = 'ACTIVE'")
    Optional<ApiKey> findActiveKeyWithTenant(@Param("keyHash") String keyHash);

    /**
     * Find all API keys for a tenant.
     */
    Optional<ApiKey> findByTenantAndName(Tenant tenant, String name);

    /**
     * Check if an API key with the given hash exists.
     */
    boolean existsByKeyHash(String keyHash);
}
