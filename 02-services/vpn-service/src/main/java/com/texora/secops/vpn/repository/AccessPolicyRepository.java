package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.AccessPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every method filters by tenant_id. */
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, UUID> {

    Optional<AccessPolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Used only to tell "does not exist" (404) from "exists in another tenant" (403). Never returns data. */
    boolean existsByIdAndTenantIdNot(UUID id, UUID tenantId);

    Page<AccessPolicy> findByTenantIdOrderBySubmittedAtDesc(UUID tenantId, Pageable pageable);

    List<AccessPolicy> findByTenantIdAndStatusOrderBySubmittedAtDesc(UUID tenantId, String status);

    @Query("select p from AccessPolicy p where p.tenantId = :tenantId and p.status = :status and p.id in "
            + "(select a.accessPolicyId from PolicyAssignment a where a.tenantId = :tenantId and a.vpnSubjectId = :subjectId)")
    List<AccessPolicy> findAssignedByStatus(@Param("tenantId") UUID tenantId, @Param("subjectId") UUID subjectId,
                                            @Param("status") String status);
}
