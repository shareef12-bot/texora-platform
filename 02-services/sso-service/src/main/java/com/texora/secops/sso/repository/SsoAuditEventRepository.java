package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.SsoAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SsoAuditEventRepository extends JpaRepository<SsoAuditEvent, UUID> {

    Page<SsoAuditEvent> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<SsoAuditEvent> findAllByTenantIdAndTargetType(UUID tenantId, String targetType, Pageable pageable);
}
