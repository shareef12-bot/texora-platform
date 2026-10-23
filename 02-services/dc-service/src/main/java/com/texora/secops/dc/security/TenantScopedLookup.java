package com.texora.secops.dc.security;

import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.exception.DcException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements B.5: cross-tenant access returns 403 Forbidden, not an empty result / 404.
 * Repositories are always tenant-filtered, so "not found in my tenant" is ambiguous; this helper disambiguates
 * with a single existence probe across tenants (id only - never returns data) and audits the attempt.
 */
@Component
public class TenantScopedLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantScopedLookup.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final DcAuditRecorder auditRecorder;

    public TenantScopedLookup(DcAuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    public <T> T require(Optional<T> found, Class<?> entityType, UUID id, CallerContext caller) {
        if (found.isPresent()) {
            return found.get();
        }
        if (existsInAnyTenant(entityType, id)) {
            LOGGER.warn("Cross-tenant access blocked: caller={} entity={}", caller.getSubjectId(), entityType.getSimpleName());
            try {
                Map<String, Object> payload = new HashMap<String, Object>();
                payload.put("entityType", entityType.getSimpleName());
                auditRecorder.recordIndependent(caller.getTenantId(), caller.getSubjectId(), "CROSS_TENANT_ACCESS_DENIED",
                        entityType.getSimpleName(), id.toString(), "DENIED", payload);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to audit cross-tenant denial (request is still denied)", e);
            }
            throw DcException.forbidden("TENANT_MISMATCH", "Resource belongs to another tenant");
        }
        throw DcException.notFound(entityType.getSimpleName() + " not found");
    }

    private boolean existsInAnyTenant(Class<?> entityType, UUID id) {
        // entityType is always a compile-time constant class of this module (never user input).
        Long count = entityManager.createQuery(
                "select count(e) from " + entityType.getSimpleName() + " e where e.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        return count != null && count > 0;
    }
}
