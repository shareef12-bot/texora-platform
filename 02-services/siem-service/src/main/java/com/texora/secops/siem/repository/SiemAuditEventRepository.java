package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SiemAuditEventRepository extends JpaRepository<SiemAuditEvent, UUID> {

    Page<SiemAuditEvent> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<SiemAuditEvent> findAllByTenantIdAndAction(UUID tenantId, String action, Pageable pageable);
}
