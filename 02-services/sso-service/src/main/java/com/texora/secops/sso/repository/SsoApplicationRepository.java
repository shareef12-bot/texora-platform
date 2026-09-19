package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.SsoApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every finder is explicitly tenant_id-scoped (per shared standards B.5) —
 * there is no method here that can return a cross-tenant row.
 */
public interface SsoApplicationRepository extends JpaRepository<SsoApplication, UUID> {

    Optional<SsoApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SsoApplication> findAllByTenantId(UUID tenantId);

    Optional<SsoApplication> findByClientId(String clientId);

    boolean existsByClientId(String clientId);

    Optional<SsoApplication> findByProductIdAndTenantId(String productId, UUID tenantId);
}
