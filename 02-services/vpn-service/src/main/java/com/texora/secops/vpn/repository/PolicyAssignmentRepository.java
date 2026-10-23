package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.PolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, UUID> {

    boolean existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(UUID tenantId, UUID accessPolicyId, UUID vpnSubjectId);
}
