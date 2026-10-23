package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcPolicyAssignment;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PolicyAssignmentRepository extends Repository<DcPolicyAssignment, UUID> {

    <S extends DcPolicyAssignment> S save(S assignment);

    boolean existsByTenantIdAndPolicyIdAndSubjectTypeAndSubjectId(UUID tenantId, UUID policyId, String subjectType, String subjectId);

    List<DcPolicyAssignment> findByTenantIdAndSubjectTypeAndSubjectIdIn(UUID tenantId, String subjectType, Collection<String> subjectIds);
}
