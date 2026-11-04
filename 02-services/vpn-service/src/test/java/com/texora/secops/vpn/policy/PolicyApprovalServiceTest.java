package com.texora.secops.vpn.policy;

import com.texora.secops.vpn.TestTransactions;
import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.AccessPolicy;
import com.texora.secops.vpn.domain.PolicyApproval;
import com.texora.secops.vpn.domain.PolicyAssignment;
import com.texora.secops.vpn.domain.VpnSubject;
import com.texora.secops.vpn.dto.AccessPolicyRequest;
import com.texora.secops.vpn.dto.PolicyDecisionRequest;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.AccessPolicyRepository;
import com.texora.secops.vpn.repository.PolicyApprovalRepository;
import com.texora.secops.vpn.repository.PolicyAssignmentRepository;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import com.texora.secops.vpn.repository.VpnSubjectRepository;
import com.texora.secops.vpn.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyApprovalServiceTest {

    private final AccessPolicyRepository policies = mock(AccessPolicyRepository.class);
    private final PolicyApprovalRepository approvals = mock(PolicyApprovalRepository.class);
    private final PolicyAssignmentRepository assignments = mock(PolicyAssignmentRepository.class);
    private final VpnSubjectRepository subjects = mock(VpnSubjectRepository.class);
    private final VpnSessionRepository sessions = mock(VpnSessionRepository.class);
    private final VpnAuditRecorder audit = mock(VpnAuditRecorder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
    private PolicyApprovalService service;

    private final UUID tenant = UUID.randomUUID();
    private final Caller submitter = new Caller(tenant, UUID.randomUUID());
    private final Caller approver = new Caller(tenant, UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new PolicyApprovalService(policies, approvals, assignments, subjects, sessions, audit,
                new PolicyConditionsCodec(), TestTransactions.template(), clock);
        when(policies.save(any(AccessPolicy.class))).thenAnswer(i -> i.getArgument(0));
    }

    private AccessPolicy policy(String status, UUID submittedBy) {
        AccessPolicy p = new AccessPolicy();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenant);
        p.setName("p");
        p.setConditions("{\"effect\":\"ALLOW\",\"mfaRequired\":true,\"deviceEnrollmentRequired\":true}");
        p.setStatus(status);
        p.setSubmittedBy(submittedBy);
        p.setSubmittedAt(clock.instant());
        when(policies.findByIdAndTenantId(p.getId(), tenant)).thenReturn(Optional.of(p));
        return p;
    }

    private PolicyDecisionRequest decision(String d) {
        PolicyDecisionRequest r = new PolicyDecisionRequest();
        r.setDecision(d);
        return r;
    }

    private AccessPolicyRequest request(Map<String, Object> conditions, UUID replaces) {
        AccessPolicyRequest r = new AccessPolicyRequest();
        r.setName("  vpn-eng  ");
        r.setConditions(conditions);
        r.setReplacesPolicyId(replaces);
        return r;
    }

    // ---------------- two-person rule ----------------

    @Test
    void submitterCanNeverApproveTheirOwnPolicy() {
        AccessPolicy p = policy(VpnConstants.POLICY_PENDING, submitter.getUserId());
        assertThatThrownBy(() -> service.decide(submitter, p.getId(), decision("APPROVE")))
                .isInstanceOfSatisfying(VpnException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(403);
                    assertThat(e.getCode()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
                });
        assertThat(p.getStatus()).isEqualTo(VpnConstants.POLICY_PENDING);
        verify(approvals, never()).save(any(PolicyApproval.class));
        verify(audit).record(eq(tenant), eq(submitter.getUserId()), eq(VpnConstants.ACTION_SELF_APPROVAL_BLOCKED),
                any(), any(), eq(VpnConstants.OUTCOME_DENIED), any());
    }

    @Test
    void submitterCannotRejectTheirOwnPolicyEither() {
        AccessPolicy p = policy(VpnConstants.POLICY_PENDING, submitter.getUserId());
        assertThatThrownBy(() -> service.decide(submitter, p.getId(), decision("REJECT")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getCode()).isEqualTo("SELF_APPROVAL_FORBIDDEN"));
        assertThat(p.getStatus()).isEqualTo(VpnConstants.POLICY_PENDING);
    }

    @Test
    void differentUserCanApprove() {
        AccessPolicy p = policy(VpnConstants.POLICY_PENDING, submitter.getUserId());
        var response = service.decide(approver, p.getId(), decision("APPROVE"));
        assertThat(response.getStatus()).isEqualTo(VpnConstants.POLICY_APPROVED);
        assertThat(p.getStatus()).isEqualTo(VpnConstants.POLICY_APPROVED);
        verify(approvals).save(any(PolicyApproval.class));
        verify(audit).record(eq(tenant), eq(approver.getUserId()), eq(VpnConstants.ACTION_POLICY_APPROVED), any(), any(),
                eq(VpnConstants.OUTCOME_SUCCESS), any());
    }

    @Test
    void rejectionReturnsPolicyToDraft() {
        AccessPolicy p = policy(VpnConstants.POLICY_PENDING, submitter.getUserId());
        assertThat(service.decide(approver, p.getId(), decision("REJECT")).getStatus()).isEqualTo(VpnConstants.POLICY_DRAFT);
    }

    @Test
    void onlyPendingPoliciesCanBeDecided() {
        for (String s : new String[]{VpnConstants.POLICY_APPROVED, VpnConstants.POLICY_DRAFT, VpnConstants.POLICY_DISABLED}) {
            AccessPolicy p = policy(s, submitter.getUserId());
            assertThatThrownBy(() -> service.decide(approver, p.getId(), decision("APPROVE")))
                    .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        }
    }

    @Test
    void approvingAReplacementRetiresTheOldPolicyAndForcesRevalidation() {
        AccessPolicy old = policy(VpnConstants.POLICY_APPROVED, UUID.randomUUID());
        AccessPolicy next = policy(VpnConstants.POLICY_PENDING, submitter.getUserId());
        next.setSupersedesPolicyId(old.getId());
        service.decide(approver, next.getId(), decision("APPROVE"));
        assertThat(old.getStatus()).isEqualTo(VpnConstants.POLICY_DISABLED);
        verify(sessions).markDueByPolicy(eq(tenant), eq(old.getId()), eq(clock.instant()));
    }

    @Test
    void rejectingAReplacementLeavesTheOldPolicyAlone() {
        AccessPolicy old = policy(VpnConstants.POLICY_APPROVED, UUID.randomUUID());
        AccessPolicy next = policy(VpnConstants.POLICY_PENDING, submitter.getUserId());
        next.setSupersedesPolicyId(old.getId());
        service.decide(approver, next.getId(), decision("REJECT"));
        assertThat(old.getStatus()).isEqualTo(VpnConstants.POLICY_APPROVED);
        verify(sessions, never()).markDueByPolicy(any(), any(), any());
    }

    @Test
    void crossTenantIs403AndUnknownIs404() {
        UUID other = UUID.randomUUID();
        when(policies.findByIdAndTenantId(other, tenant)).thenReturn(Optional.empty());
        when(policies.existsByIdAndTenantIdNot(other, tenant)).thenReturn(true);
        assertThatThrownBy(() -> service.decide(approver, other, decision("APPROVE")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        UUID missing = UUID.randomUUID();
        when(policies.findByIdAndTenantId(missing, tenant)).thenReturn(Optional.empty());
        when(policies.existsByIdAndTenantIdNot(missing, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.decide(approver, missing, decision("APPROVE")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    // ---------------- submit / disable / assign / list ----------------

    @Test
    void submitCreatesPendingPolicyOwnedBySubmitterAndAudits() {
        var response = service.submit(submitter, request(new LinkedHashMap<>(), null));
        assertThat(response.getStatus()).isEqualTo(VpnConstants.POLICY_PENDING);
        assertThat(response.getSubmittedBy()).isEqualTo(submitter.getUserId());
        assertThat(response.getName()).isEqualTo("vpn-eng");
        verify(audit).record(eq(tenant), eq(submitter.getUserId()), eq(VpnConstants.ACTION_POLICY_SUBMITTED), any(), any(),
                eq(VpnConstants.OUTCOME_SUCCESS), any());
    }

    @Test
    void submitRejectsInvalidConditionsWith422() {
        assertThatThrownBy(() -> service.submit(submitter, request(new LinkedHashMap<>(Map.of("typo", 1)), null)))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(422));
        verify(policies, never()).save(any(AccessPolicy.class));
    }

    @Test
    void onlyApprovedPoliciesCanBeReplaced() {
        AccessPolicy draft = policy(VpnConstants.POLICY_DRAFT, UUID.randomUUID());
        assertThatThrownBy(() -> service.submit(submitter, request(new LinkedHashMap<>(), draft.getId())))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getCode()).isEqualTo("POLICY_NOT_REPLACEABLE"));
    }

    @Test
    void disableRetiresPolicyAndMarksSessionsDue() {
        AccessPolicy p = policy(VpnConstants.POLICY_APPROVED, UUID.randomUUID());
        assertThat(service.disable(approver, p.getId()).getStatus()).isEqualTo(VpnConstants.POLICY_DISABLED);
        verify(sessions).markDueByPolicy(tenant, p.getId(), clock.instant());
        AccessPolicy pending = policy(VpnConstants.POLICY_PENDING, UUID.randomUUID());
        assertThatThrownBy(() -> service.disable(approver, pending.getId()))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void assignRules() {
        AccessPolicy approved = policy(VpnConstants.POLICY_APPROVED, UUID.randomUUID());
        UUID subjectId = UUID.randomUUID();
        VpnSubject subject = new VpnSubject();
        subject.setId(subjectId);
        when(subjects.findByIdAndTenantId(subjectId, tenant)).thenReturn(Optional.of(subject));
        when(assignments.save(any(PolicyAssignment.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.assign(approver, approved.getId(), subjectId).getSubjectId()).isEqualTo(subjectId);

        when(assignments.existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(tenant, approved.getId(), subjectId)).thenReturn(true);
        assertThatThrownBy(() -> service.assign(approver, approved.getId(), subjectId))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(409));

        AccessPolicy draft = policy(VpnConstants.POLICY_DRAFT, UUID.randomUUID());
        assertThatThrownBy(() -> service.assign(approver, draft.getId(), subjectId))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(422));

        UUID foreign = UUID.randomUUID();
        when(subjects.findByIdAndTenantId(foreign, tenant)).thenReturn(Optional.empty());
        when(subjects.existsByIdAndTenantIdNot(foreign, tenant)).thenReturn(true);
        assertThatThrownBy(() -> service.assign(approver, approved.getId(), foreign))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        UUID missing = UUID.randomUUID();
        when(subjects.findByIdAndTenantId(missing, tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(approver, approved.getId(), missing))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void effectivePolicyListShowsOnlyApproved() {
        AccessPolicy p = policy(VpnConstants.POLICY_APPROVED, UUID.randomUUID());
        when(policies.findByTenantIdAndStatusOrderBySubmittedAtDesc(tenant, VpnConstants.POLICY_APPROVED)).thenReturn(List.of(p));
        var views = service.listEffective(tenant);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).getEffect()).isEqualTo("ALLOW");
    }
}
