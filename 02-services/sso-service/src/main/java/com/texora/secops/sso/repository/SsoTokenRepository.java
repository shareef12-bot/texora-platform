package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.SsoToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SsoTokenRepository extends JpaRepository<SsoToken, UUID> {

    List<SsoToken> findAllBySessionIdAndTenantId(UUID sessionId, UUID tenantId);
}
