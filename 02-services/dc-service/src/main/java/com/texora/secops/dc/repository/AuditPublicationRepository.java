package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcAuditPublication;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditPublicationRepository extends Repository<DcAuditPublication, UUID> {

    <S extends DcAuditPublication> S save(S publication);

    Optional<DcAuditPublication> findByTenantIdAndAuditEventId(UUID tenantId, UUID auditEventId);

    /**
     * SYSTEM-ONLY, intentionally cross-tenant: used by the background retry sweeper, which has no request tenant.
     * SKIP LOCKED lets several pods sweep concurrently without double-claiming rows.
     */
    @Query(value = "select * from dc.dc_audit_publication where published_at is null and next_attempt_at <= :now "
            + "order by next_attempt_at limit :limit for update skip locked", nativeQuery = true)
    List<DcAuditPublication> claimDueAcrossTenants(@Param("now") Instant now, @Param("limit") int limit);
}
