package com.texora.secops.ldap.repository;

import com.texora.secops.ldap.domain.LdapAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for {@link LdapAuditEvent}.
 * Every query is tenant-filtered (requirement B.5).
 * This table is append-only; no update or delete methods are exposed.
 */
public interface AuditEventRepository extends JpaRepository<LdapAuditEvent, UUID> {

    Page<LdapAuditEvent> findAllByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM LdapAuditEvent e WHERE e.tenantId = :tenantId " +
           "AND (:targetType IS NULL OR e.targetType = :targetType) " +
           "AND (:targetId IS NULL OR e.targetId = :targetId) " +
           "AND (:action IS NULL OR e.action = :action) " +
           "AND (:from IS NULL OR e.occurredAt >= :from) " +
           "AND (:to IS NULL OR e.occurredAt <= :to) " +
           "ORDER BY e.occurredAt DESC")
    Page<LdapAuditEvent> search(@Param("tenantId") UUID tenantId,
                                 @Param("action") String action,
                                 @Param("targetType") String targetType,
                                 @Param("targetId") String targetId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 Pageable pageable);
}
