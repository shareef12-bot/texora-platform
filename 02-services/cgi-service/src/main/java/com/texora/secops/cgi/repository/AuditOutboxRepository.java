package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.audit.domain.AuditOutbox;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface AuditOutboxRepository extends Repository<AuditOutbox, UUID> {

    AuditOutbox save(AuditOutbox outbox);

    /** @see SystemWide - the forwarding job addresses rows by their own id and carries tenant_id with them. */
    @SystemWide
    Optional<AuditOutbox> findById(UUID id);

    /** @see SystemWide - cross-tenant by design: the scheduled retry drains everyone's un-forwarded events. */
    @SystemWide
    List<AuditOutbox> findTop100ByPublishedAtIsNullAndAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAtAsc(int maxAttempts, Instant createdBefore);
}
