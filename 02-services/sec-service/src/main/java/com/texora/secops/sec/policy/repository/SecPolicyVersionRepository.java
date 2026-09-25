package com.texora.secops.sec.policy.repository;

import com.texora.secops.sec.policy.domain.SecPolicyVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecPolicyVersionRepository extends JpaRepository<SecPolicyVersion, UUID> {

    // Fetch ACTIVE versions matching a subject/resource/action for evaluation
    @Query("""
        SELECT pv FROM SecPolicyVersion pv
        JOIN SecPolicy p ON pv.policyId = p.id
        WHERE pv.tenantId = :tenantId
          AND pv.status = 'ACTIVE'
          AND p.resource = :resource
          AND p.action = :action
    """)
    List<SecPolicyVersion> findActiveVersionsForEvaluation(
            @Param("tenantId") UUID tenantId,
            @Param("resource") String resource,
            @Param("action") String action);

    Page<SecPolicyVersion> findByPolicyIdAndTenantId(UUID policyId, UUID tenantId, Pageable pageable);

    Optional<SecPolicyVersion> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Integer> findMaxVersionNumberByPolicyId(UUID policyId);

    @Query("SELECT MAX(pv.versionNumber) FROM SecPolicyVersion pv WHERE pv.policyId = :policyId")
    Optional<Integer> findMaxVersionNumber(@Param("policyId") UUID policyId);
}
