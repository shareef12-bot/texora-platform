package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.PolicyApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyApprovalRepository extends JpaRepository<PolicyApproval, UUID> {

    List<PolicyApproval> findByAccessPolicyIdAndTenantId(UUID accessPolicyId, UUID tenantId);
}
