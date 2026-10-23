package com.texora.secops.vpn.policy;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.AccessPolicy;
import com.texora.secops.vpn.domain.PolicyApproval;
import com.texora.secops.vpn.domain.PolicyAssignment;
import com.texora.secops.vpn.dto.AccessPolicyRequest;
import com.texora.secops.vpn.dto.AccessPolicyResponse;
import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.dto.PolicyAssignmentResponse;
import com.texora.secops.vpn.dto.PolicyDecisionRequest;
import com.texora.secops.vpn.dto.PolicyDecisionResponse;
import com.texora.secops.vpn.dto.SecurityPolicyView;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.AccessPolicyRepository;
import com.texora.secops.vpn.repository.PolicyApprovalRepository;
import com.texora.secops.vpn.repository.PolicyAssignmentRepository;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import com.texora.secops.vpn.repository.VpnSubjectRepository;
import com.texora.secops.vpn.security.Caller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Access policy lifecycle with TWO-PERSON approval. The rule is enforced HERE (service layer), not in documentation:
 * the submitter can never approve or reject their own submission, and an attempt is itself audited.
 *
 * A change to an existing policy is a NEW submission with replacesPolicyId; approving it disables the old policy and
 * immediately marks every ACTIVE session on the old policy due for re-validation.
 */
@Service
public class PolicyApprovalService {

    private final AccessPolicyRepository policies;
    private final PolicyApprovalRepository approvals;
    private final PolicyAssignmentRepository assignments;
    private final VpnSubjectRepository subjects;
    private final VpnSessionRepository sessions;
    private final VpnAuditRecorder audit;
    private final PolicyConditionsCodec codec;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PolicyApprovalService(AccessPolicyRepository policies, PolicyApprovalRepository approvals,
                                 PolicyAssignmentRepository assignments, VpnSubjectRepository subjects,
                                 VpnSessionRepository sessions, VpnAuditRecorder audit, PolicyConditionsCodec codec,
                                 TransactionTemplate tx, Clock clock) {
        this.policies = policies;
        this.approvals = approvals;
        this.assignments = assignments;
        this.subjects = subjects;
        this.sessions = sessions;
        this.audit = audit;
        this.codec = codec;
        this.tx = tx;
        this.clock = clock;
    }

    public AccessPolicyResponse submit(Caller caller, AccessPolicyRequest request) {
        AccessPolicyConditions conditions;
        try {
            conditions = codec.fromMap(request.getConditions());
        } catch (PolicyConditionsException e) {
            throw VpnException.unprocessable("POLICY_CONDITIONS_INVALID", e.getMessage());
        }
        UUID tenantId = caller.getTenantId();
        if (request.getReplacesPolicyId() != null) {
            AccessPolicy old = loadOrThrow(request.getReplacesPolicyId(), tenantId);
            if (!VpnConstants.POLICY_APPROVED.equals(old.getStatus())) {
                throw VpnException.unprocessable("POLICY_NOT_REPLACEABLE", "Only an APPROVED policy can be replaced");
            }
        }
        return tx.execute(status -> {
            AccessPolicy p = new AccessPolicy();
            p.setId(UUID.randomUUID());
            p.setTenantId(tenantId);
            p.setName(request.getName().trim());
            p.setConditions(codec.toJson(conditions));
            p.setStatus(VpnConstants.POLICY_PENDING);
            p.setSubmittedBy(caller.getUserId());
            p.setSubmittedAt(clock.instant());
            p.setSupersedesPolicyId(request.getReplacesPolicyId());
            policies.save(p);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", p.getName());
            payload.put("supersedes", request.getReplacesPolicyId() == null ? null : request.getReplacesPolicyId().toString());
            payload.put("effect", conditions.getEffect());
            payload.put("mfaRequired", conditions.getMfaRequired());
            audit.record(tenantId, caller.getUserId(), VpnConstants.ACTION_POLICY_SUBMITTED, "ACCESS_POLICY",
                    p.getId().toString(), VpnConstants.OUTCOME_SUCCESS, payload);
            return toResponse(p);
        });
    }

    public PageResponse<AccessPolicyResponse> list(UUID tenantId, int page, int size) {
        Page<AccessPolicy> result = policies.findByTenantIdOrderBySubmittedAtDesc(tenantId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        List<AccessPolicyResponse> items = new ArrayList<>();
        for (AccessPolicy p : result.getContent()) {
            items.add(toResponse(p));
        }
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /** GET /api/v1/policies: the APPROVED policy set the session decisions are made against. */
    public List<SecurityPolicyView> listEffective(UUID tenantId) {
        List<SecurityPolicyView> views = new ArrayList<>();
        for (AccessPolicy p : policies.findByTenantIdAndStatusOrderBySubmittedAtDesc(tenantId, VpnConstants.POLICY_APPROVED)) {
            SecurityPolicyView v = new SecurityPolicyView();
            v.setId(p.getId());
            v.setName(p.getName());
            Map<String, Object> conditions = codec.toMap(p.getConditions());
            Object effect = conditions.get("effect");
            v.setEffect(effect == null ? null : effect.toString());
            v.setConditions(conditions);
            views.add(v);
        }
        return views;
    }

    public PolicyDecisionResponse decide(Caller caller, UUID policyId, PolicyDecisionRequest request) {
        UUID tenantId = caller.getTenantId();
        AccessPolicy preview = loadOrThrow(policyId, tenantId);
        if (preview.getSubmittedBy().equals(caller.getUserId())) {
            blockSelfApproval(caller, preview);
        }
        return tx.execute(status -> {
            AccessPolicy policy = loadOrThrow(policyId, tenantId);
            if (policy.getSubmittedBy().equals(caller.getUserId())) {
                // Defense in depth only: the audited block above already handled this. Throw directly - an audit row
                // written inside this transaction would be rolled back by the throw.
                throw VpnException.forbidden("SELF_APPROVAL_FORBIDDEN", "The submitter cannot approve or reject their own change");
            }
            if (!VpnConstants.POLICY_PENDING.equals(policy.getStatus())) {
                throw VpnException.conflict("POLICY_NOT_PENDING", "Policy is not awaiting approval");
            }
            boolean approve = "APPROVE".equals(request.getDecision());
            Instant now = clock.instant();

            PolicyApproval row = new PolicyApproval();
            row.setId(UUID.randomUUID());
            row.setTenantId(tenantId);
            row.setAccessPolicyId(policy.getId());
            row.setApproverId(caller.getUserId());
            row.setDecision(approve ? "APPROVED" : "REJECTED");
            row.setComment(request.getComment());
            row.setDecidedAt(now);
            approvals.save(row);

            policy.setStatus(approve ? VpnConstants.POLICY_APPROVED : VpnConstants.POLICY_DRAFT);
            policies.save(policy); // @Version: a concurrent second decision fails with a 409

            if (approve && policy.getSupersedesPolicyId() != null) {
                retire(tenantId, policy.getSupersedesPolicyId(), now);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("submittedBy", policy.getSubmittedBy().toString());
            payload.put("supersedes", policy.getSupersedesPolicyId() == null ? null : policy.getSupersedesPolicyId().toString());
            audit.record(tenantId, caller.getUserId(),
                    approve ? VpnConstants.ACTION_POLICY_APPROVED : VpnConstants.ACTION_POLICY_REJECTED,
                    "ACCESS_POLICY", policy.getId().toString(), VpnConstants.OUTCOME_SUCCESS, payload);

            PolicyDecisionResponse response = new PolicyDecisionResponse();
            response.setPolicyId(policy.getId());
            response.setStatus(policy.getStatus());
            response.setApproverId(caller.getUserId());
            response.setDecidedAt(now);
            return response;
        });
    }

    /**
     * Kill switch. Disabling only ever REDUCES access, so it needs vpn.policy.approve but not a second person.
     * Every ACTIVE session on the policy is marked due for re-validation in the same transaction.
     */
    public AccessPolicyResponse disable(Caller caller, UUID policyId) {
        UUID tenantId = caller.getTenantId();
        loadOrThrow(policyId, tenantId);
        return tx.execute(status -> {
            AccessPolicy policy = loadOrThrow(policyId, tenantId);
            if (!VpnConstants.POLICY_APPROVED.equals(policy.getStatus())) {
                throw VpnException.conflict("POLICY_NOT_APPROVED", "Only an APPROVED policy can be disabled");
            }
            retire(tenantId, policyId, clock.instant());
            audit.record(tenantId, caller.getUserId(), VpnConstants.ACTION_POLICY_DISABLED, "ACCESS_POLICY",
                    policyId.toString(), VpnConstants.OUTCOME_SUCCESS, new LinkedHashMap<>());
            return toResponse(loadOrThrow(policyId, tenantId));
        });
    }

    public PolicyAssignmentResponse assign(Caller caller, UUID policyId, UUID subjectId) {
        UUID tenantId = caller.getTenantId();
        AccessPolicy policy = loadOrThrow(policyId, tenantId);
        if (!VpnConstants.POLICY_APPROVED.equals(policy.getStatus())) {
            throw VpnException.unprocessable("POLICY_NOT_APPROVED", "Only an APPROVED policy can be assigned");
        }
        if (subjects.findByIdAndTenantId(subjectId, tenantId).isEmpty()) {
            if (subjects.existsByIdAndTenantIdNot(subjectId, tenantId)) {
                throw VpnException.forbidden("TENANT_FORBIDDEN", "Resource belongs to another tenant");
            }
            throw VpnException.notFound("SUBJECT_NOT_FOUND", "Subject not found");
        }
        if (assignments.existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(tenantId, policyId, subjectId)) {
            throw VpnException.conflict("ASSIGNMENT_EXISTS", "Policy is already assigned to this subject");
        }
        return tx.execute(status -> {
            PolicyAssignment a = new PolicyAssignment();
            a.setId(UUID.randomUUID());
            a.setTenantId(tenantId);
            a.setAccessPolicyId(policyId);
            a.setVpnSubjectId(subjectId);
            a.setAssignedBy(caller.getUserId());
            a.setAssignedAt(clock.instant());
            assignments.save(a);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("subjectId", subjectId.toString());
            audit.record(tenantId, caller.getUserId(), VpnConstants.ACTION_POLICY_ASSIGNED, "ACCESS_POLICY",
                    policyId.toString(), VpnConstants.OUTCOME_SUCCESS, payload);
            PolicyAssignmentResponse r = new PolicyAssignmentResponse();
            r.setId(a.getId());
            r.setAccessPolicyId(policyId);
            r.setSubjectId(subjectId);
            r.setAssignedBy(a.getAssignedBy());
            r.setAssignedAt(a.getAssignedAt());
            return r;
        });
    }

    // ------------------------------------------------------------------

    private void retire(UUID tenantId, UUID policyId, Instant now) {
        AccessPolicy old = loadOrThrow(policyId, tenantId);
        old.setStatus(VpnConstants.POLICY_DISABLED);
        policies.save(old);
        sessions.markDueByPolicy(tenantId, policyId, now); // event-driven re-validation trigger
    }

    private void blockSelfApproval(Caller caller, AccessPolicy policy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", "submitter attempted to decide their own submission");
        audit.record(caller.getTenantId(), caller.getUserId(), VpnConstants.ACTION_SELF_APPROVAL_BLOCKED, "ACCESS_POLICY",
                policy.getId().toString(), VpnConstants.OUTCOME_DENIED, payload);
        throw VpnException.forbidden("SELF_APPROVAL_FORBIDDEN", "The submitter cannot approve or reject their own change");
    }

    /** 404 when it does not exist anywhere; 403 when it exists in another tenant (B.5). */
    private AccessPolicy loadOrThrow(UUID id, UUID tenantId) {
        return policies.findByIdAndTenantId(id, tenantId).orElseThrow(() -> {
            if (policies.existsByIdAndTenantIdNot(id, tenantId)) {
                return VpnException.forbidden("TENANT_FORBIDDEN", "Resource belongs to another tenant");
            }
            return VpnException.notFound("POLICY_NOT_FOUND", "Access policy not found");
        });
    }

    private AccessPolicyResponse toResponse(AccessPolicy p) {
        AccessPolicyResponse r = new AccessPolicyResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setStatus(p.getStatus());
        r.setConditions(codec.toMap(p.getConditions()));
        r.setSubmittedBy(p.getSubmittedBy());
        r.setSubmittedAt(p.getSubmittedAt());
        r.setSupersedesPolicyId(p.getSupersedesPolicyId());
        return r;
    }
}
