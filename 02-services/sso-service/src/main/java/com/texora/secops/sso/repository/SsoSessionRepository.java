package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.SessionStatus;
import com.texora.secops.sso.domain.SsoSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SsoSessionRepository extends JpaRepository<SsoSession, UUID> {

    Optional<SsoSession> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SsoSession> findAllByTenantId(UUID tenantId);

    List<SsoSession> findAllByDcUserIdAndTenantIdAndStatus(UUID dcUserId, UUID tenantId, SessionStatus status);
}
