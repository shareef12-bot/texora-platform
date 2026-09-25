package com.texora.secops.sec.policy.repository;

import com.texora.secops.sec.policy.domain.SecPolicyEvaluationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface SecPolicyEvaluationLogRepository extends JpaRepository<SecPolicyEvaluationLog, UUID> {

    Page<SecPolicyEvaluationLog> findByTenantIdOrderByEvaluatedAtDesc(UUID tenantId, Pageable pageable);

    Page<SecPolicyEvaluationLog> findByTenantIdAndCallerServiceOrderByEvaluatedAtDesc(
            UUID tenantId, String callerService, Pageable pageable);

    // Count DENY decisions for a caller for metrics (default-deny rate)
    @Query("""
        SELECT COUNT(l) FROM SecPolicyEvaluationLog l
        WHERE l.tenantId = :tenantId
          AND l.callerService = :callerService
          AND l.decision = 'DENY'
          AND l.evaluatedAt >= :since
    """)
    long countDenyDecisionsByCallerSince(
            @Param("tenantId") UUID tenantId,
            @Param("callerService") String callerService,
            @Param("since") Instant since);
}
