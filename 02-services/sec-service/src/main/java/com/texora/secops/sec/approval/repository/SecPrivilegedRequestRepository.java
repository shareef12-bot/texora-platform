package com.texora.secops.sec.approval.repository;

import com.texora.secops.sec.approval.domain.SecPrivilegedRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecPrivilegedRequestRepository extends JpaRepository<SecPrivilegedRequest, UUID> {

    Page<SecPrivilegedRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<SecPrivilegedRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    // Find PENDING requests that should be expired
    List<SecPrivilegedRequest> findByStatusAndExpiresAtBefore(String status, Instant now);

    // Find approved request by type + target for checking before privileged ops
    @Query("""
        SELECT r FROM SecPrivilegedRequest r
        WHERE r.tenantId = :tenantId
          AND r.requestType = :requestType
          AND r.targetRef = :targetRef
          AND r.status = 'APPROVED'
        ORDER BY r.createdAt DESC
        LIMIT 1
    """)
    Optional<SecPrivilegedRequest> findLatestApproved(
            @Param("tenantId") UUID tenantId,
            @Param("requestType") String requestType,
            @Param("targetRef") String targetRef);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
