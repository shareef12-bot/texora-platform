package com.texora.secops.sec.approval.repository;

import com.texora.secops.sec.approval.domain.SecApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecApprovalDecisionRepository extends JpaRepository<SecApprovalDecision, UUID> {

    List<SecApprovalDecision> findByPrivilegedRequestId(UUID privilegedRequestId);

    // Separation of duties check — reject if this approver already submitted the request
    Optional<SecApprovalDecision> findByPrivilegedRequestIdAndApproverId(
            UUID privilegedRequestId, UUID approverId);

    long countByPrivilegedRequestIdAndDecision(UUID privilegedRequestId, String decision);
}
