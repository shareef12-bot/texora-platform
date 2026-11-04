package com.texora.qap.repository;

import com.texora.qap.domain.QapAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only query access to the append-only audit log.
 * Writes go through the AuditingAspect + shared Audit SDK.
 */
@Repository
public interface QapAuditEventRepository extends JpaRepository<QapAuditEvent, UUID> {

    Page<QapAuditEvent> findAllByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);

    Page<QapAuditEvent> findByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);

    Page<QapAuditEvent> findAllByTenantIdAndActorId(UUID tenantId, UUID actorId, Pageable pageable);

    Page<QapAuditEvent> findAllByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID tenantId, Instant from, Instant to, Pageable pageable);
}
