package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.VpnSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped methods are used by the REST path. Methods named "...AcrossTenants" are used ONLY by the
 * control-plane's own background jobs (re-validation, watchdog); they act as the system, and every follow-up
 * action is performed with the session's own tenant_id.
 */
public interface VpnSessionRepository extends JpaRepository<VpnSession, UUID> {

    Optional<VpnSession> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantIdNot(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VpnSession s where s.id = :id and s.tenantId = :tenantId")
    Optional<VpnSession> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Page<VpnSession> findByTenantIdOrderByEstablishedAtDesc(UUID tenantId, Pageable pageable);

    Page<VpnSession> findByTenantIdAndStatusOrderByEstablishedAtDesc(UUID tenantId, String status, Pageable pageable);

    // ---------------- system (background job) queries ----------------

    @Query("select s from VpnSession s where s.status = 'ACTIVE' and s.nextValidationAt <= :now order by s.nextValidationAt")
    List<VpnSession> findDueForRevalidationAcrossTenants(@Param("now") Instant now, Pageable pageable);

    @Query("select s from VpnSession s where s.status = 'ACTIVE' and s.lastValidatedAt < :cutoff order by s.lastValidatedAt")
    List<VpnSession> findStaleAcrossTenants(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Query("select s from VpnSession s where s.status = 'ACTIVE' and s.expiresAt <= :now order by s.expiresAt")
    List<VpnSession> findExpiredAcrossTenants(@Param("now") Instant now, Pageable pageable);

    /** Atomic claim so two replicas never revalidate the same session at once. Returns 1 if this caller won. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VpnSession s set s.nextValidationAt = :leaseUntil "
            + "where s.id = :id and s.tenantId = :tenantId and s.status = 'ACTIVE' and s.nextValidationAt <= :now")
    int claimForRevalidation(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                             @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VpnSession s set s.lastValidatedAt = :now, s.nextValidationAt = :next "
            + "where s.id = :id and s.tenantId = :tenantId and s.status = 'ACTIVE'")
    int markValidated(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                      @Param("now") Instant now, @Param("next") Instant next);

    /** Event-driven trigger: a dependent policy changed, so every ACTIVE session on it is re-checked immediately. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VpnSession s set s.nextValidationAt = :now "
            + "where s.tenantId = :tenantId and s.accessPolicyId = :policyId and s.status = 'ACTIVE'")
    int markDueByPolicy(@Param("tenantId") UUID tenantId, @Param("policyId") UUID policyId, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VpnSession s set s.nextValidationAt = :now "
            + "where s.tenantId = :tenantId and s.vpnSubjectId = :subjectId and s.status = 'ACTIVE'")
    int markDueBySubject(@Param("tenantId") UUID tenantId, @Param("subjectId") UUID subjectId, @Param("now") Instant now);

    @Query("select min(s.lastValidatedAt) from VpnSession s where s.status = 'ACTIVE'")
    Optional<Instant> oldestValidationAcrossTenants();

    long countByStatus(String status);
}
