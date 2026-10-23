package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcAuditEvent;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Insert + tenant-scoped read only. No update/delete methods exist on purpose (immutability). */
public interface AuditEventRepository extends Repository<DcAuditEvent, UUID> {

    <S extends DcAuditEvent> S save(S event);

    Optional<DcAuditEvent> findByTenantIdAndId(UUID tenantId, UUID id);
}
