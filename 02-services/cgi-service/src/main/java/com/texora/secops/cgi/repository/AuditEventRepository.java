package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    Optional<AuditEvent> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Empty-string action/outcome mean 'any' (avoids untyped-null parameters on PostgreSQL). */
    @Query("select e from AuditEvent e where e.tenantId = :tenantId "
            + "and (:action = '' or e.action = :action) "
            + "and (:outcome = '' or e.outcome = :outcome) "
            + "and e.occurredAt >= :from and e.occurredAt <= :to")
    Page<AuditEvent> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("action") String action,
                                      @Param("outcome") String outcome, @Param("from") Instant from,
                                      @Param("to") Instant to, Pageable pageable);
}
