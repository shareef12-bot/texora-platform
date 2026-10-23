package com.texora.secops.dc.policy.service;

import com.texora.secops.dc.DcConstants;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcPolicy;
import com.texora.secops.dc.domain.DcPolicyAssignment;
import com.texora.secops.dc.domain.DcUser;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.GroupRepository;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.PolicyAssignmentRepository;
import com.texora.secops.dc.repository.PolicyRepository;
import com.texora.secops.dc.repository.UserRepository;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.TenantScopedLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * POST /api/v1/policy-assignment - the PRIMARY privilege-escalation surface (LLD 9.4).
 * Guarded by: the strictest role (dc.policy.assign, enforced by RbacInterceptor), tenant scoping, subject/policy type
 * agreement, and a self-escalation block (a caller can't grant policy to themselves, a group they belong to, or a
 * role they hold). EVERY attempt - allowed or denied - is audited; denials commit even though the request rolls back.
 */
@Service
public class PolicyAssignmentService {

    private final PolicyRepository policies;
    private final PolicyAssignmentRepository assignments;
    private final UserRepository users;
    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final DcAuditRecorder audit;
    private final TenantScopedLookup lookup;
    private final TimeSource time;

    public PolicyAssignmentService(PolicyRepository policies, PolicyAssignmentRepository assignments, UserRepository users,
                                   GroupRepository groups, MembershipRepository memberships, DcAuditRecorder audit,
                                   TenantScopedLookup lookup, TimeSource time) {
        this.policies = policies;
        this.assignments = assignments;
        this.users = users;
        this.groups = groups;
        this.memberships = memberships;
        this.audit = audit;
        this.lookup = lookup;
        this.time = time;
    }

    @Transactional
    public DcPolicyAssignment assign(CallerContext caller, UUID policyId, String subjectType, String subjectId) {
        UUID tenantId = caller.getTenantId();
        DcPolicy policy = lookup.require(policies.findByTenantIdAndId(tenantId, policyId), DcPolicy.class, policyId, caller);
        if (!policy.getSubjectType().equals(subjectType)) {
            throw DcException.unprocessable("SUBJECT_TYPE_MISMATCH",
                    "Policy targets " + policy.getSubjectType() + " subjects but assignment is for " + subjectType);
        }
        verifySubjectExists(caller, subjectType, subjectId);
        if (isSelfEscalation(caller, subjectType, subjectId)) {
            Map<String, Object> denied = new HashMap<String, Object>();
            denied.put("policyId", policyId.toString());
            denied.put("subjectType", subjectType);
            denied.put("subjectId", subjectId);
            audit.recordIndependent(tenantId, caller.getSubjectId(), "POLICY_ASSIGNMENT_DENIED", "POLICY",
                    policyId.toString(), "DENIED", denied);
            throw DcException.forbidden("PRIVILEGE_ESCALATION_BLOCKED", "You cannot assign policy to yourself or to a group/role you belong to");
        }
        if (assignments.existsByTenantIdAndPolicyIdAndSubjectTypeAndSubjectId(tenantId, policyId, subjectType, subjectId)) {
            throw DcException.conflict("ASSIGNMENT_EXISTS", "Policy is already assigned to this subject");
        }
        DcPolicyAssignment a = new DcPolicyAssignment();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setPolicyId(policyId);
        a.setSubjectType(subjectType);
        a.setSubjectId(subjectId);
        a.setAssignedBy(caller.getSubjectId());
        a.setAssignedAt(time.now());
        assignments.save(a);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("policyId", policyId.toString());
        payload.put("subjectType", subjectType);
        payload.put("subjectId", subjectId);
        payload.put("effect", policy.getEffect());
        audit.record(tenantId, caller.getSubjectId(), "POLICY_ASSIGNED", "POLICY_ASSIGNMENT", a.getId().toString(), "SUCCESS", payload);
        return a;
    }

    private void verifySubjectExists(CallerContext caller, String subjectType, String subjectId) {
        if (DcConstants.SUBJECT_ROLE.equals(subjectType)) {
            return; // roles are names owned by SSO/RBAC, not a DC table
        }
        UUID id;
        try {
            id = UUID.fromString(subjectId);
        } catch (IllegalArgumentException e) {
            throw DcException.unprocessable("INVALID_SUBJECT", "subjectId must be a UUID for " + subjectType + " subjects");
        }
        if (DcConstants.SUBJECT_USER.equals(subjectType)) {
            lookup.require(users.findByTenantIdAndId(caller.getTenantId(), id), DcUser.class, id, caller);
        } else {
            lookup.require(groups.findByTenantIdAndId(caller.getTenantId(), id), DcGroup.class, id, caller);
        }
    }

    private boolean isSelfEscalation(CallerContext caller, String subjectType, String subjectId) {
        if (DcConstants.SUBJECT_USER.equals(subjectType)) {
            return subjectId.equalsIgnoreCase(String.valueOf(caller.getSubjectId()));
        }
        if (DcConstants.SUBJECT_GROUP.equals(subjectType)) {
            return memberships.existsMembership(caller.getTenantId(), UUID.fromString(subjectId), caller.getSubjectId());
        }
        return caller.getRoles() != null && caller.getRoles().contains(subjectId);
    }
}
