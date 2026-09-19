package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.SsoOidcSamlConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SsoOidcSamlConfigRepository extends JpaRepository<SsoOidcSamlConfig, UUID> {

    Optional<SsoOidcSamlConfig> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SsoOidcSamlConfig> findAllByTenantId(UUID tenantId);

    List<SsoOidcSamlConfig> findAllByApplicationIdAndTenantId(UUID applicationId, UUID tenantId);

    /**
     * Deliberately NOT tenant-scoped: called from {@code SsoRegisteredClientRepository}
     * during /oauth2/authorize, before any tenant is known (there is no bearer token
     * yet — this IS the request that will eventually produce one). Safe because the
     * lookup key (applicationId, resolved from a globally-unique client_id) already
     * pins the result to exactly one tenant via the owning sso_application row.
     */
    List<SsoOidcSamlConfig> findAllByApplicationId(UUID applicationId);
}

