package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.SsoConsentGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SsoConsentGrantRepository extends JpaRepository<SsoConsentGrant, UUID> {

    Optional<SsoConsentGrant> findByDcUserIdAndApplicationIdAndTenantIdAndRevokedAtIsNull(
            UUID dcUserId, UUID applicationId, UUID tenantId);
}
