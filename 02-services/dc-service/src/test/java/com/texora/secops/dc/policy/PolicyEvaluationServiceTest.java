package com.texora.secops.dc.policy;

import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcPolicy;
import com.texora.secops.dc.domain.DcPolicyAssignment;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.policy.dto.PolicyDecision;
import com.texora.secops.dc.policy.service.PolicyEvaluationService;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.PolicyAssignmentRepository;
import com.texora.secops.dc.repository.PolicyRepository;
import com.texora.secops.dc.security.PolicyDecisionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyEvaluationServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID requester = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private PolicyRepository policies;
    private PolicyAssignmentRepository assignments;
    private MembershipRepository memberships;
    private PolicyDecisionClient sec;
    private DcAuditRecorder audit;

    @BeforeEach
    void setUp() {
        policies = mock(PolicyRepository.class);
        assignments = mock(PolicyAssignmentRepository.class);
        memberships = mock(MembershipRepository.class);
        sec = mock(PolicyDecisionClient.class);
        audit = mock(DcAuditRecorder.class);
    }

    private PolicyEvaluationService service(boolean secConsult) {
        return new PolicyEvaluationService(policies, assignments, memberships, sec, audit, secConsult);
    }

    private DcPolicy policy(String effect, String resource, String action) {
        DcPolicy p = new DcPolicy();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenant);
        p.setEffect(effect);
        p.setResource(resource);
        p.setAction(action);
        p.setSubjectType("USER");
        return p;
    }

    private void assignAll(DcPolicy... ps) {
        java.util.List<DcPolicyAssignment> list = new java.util.ArrayList<DcPolicyAssignment>();
        for (DcPolicy p : ps) {
            DcPolicyAssignment a = new DcPolicyAssignment();
            a.setPolicyId(p.getId());
            list.add(a);
        }
        when(assignments.findByTenantIdAndSubjectTypeAndSubjectIdIn(eq(tenant), eq("USER"), anyCollection())).thenReturn(list);
        when(policies.findByTenantIdAndIdIn(eq(tenant), anyCollection())).thenReturn(Arrays.asList(ps));
    }

    private void verifyAudited(String decision) {
        verify(audit).recordIndependent(eq(tenant), eq(requester), eq("POLICY_DECISION"), anyString(), anyString(), eq(decision), anyMap());
    }

    @Test
    void noMatchingPolicyDeniesAndAudits() {
        PolicyDecision d = service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect");
        assertEquals("DENY", d.getDecision());
        assertEquals("NO_MATCHING_POLICY", d.getReason());
        verifyAudited("DENY");
    }

    @Test
    void singleAllowAllows() {
        DcPolicy allow = policy("ALLOW", "vpn", "connect");
        assignAll(allow);
        PolicyDecision d = service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect");
        assertEquals("ALLOW", d.getDecision());
        assertEquals(Collections.singletonList(allow.getId()), d.getMatchedPolicyIds());
        verifyAudited("ALLOW");
    }

    @Test
    void conflictingPoliciesDeny() {
        assignAll(policy("ALLOW", "vpn", "connect"), policy("DENY", "vpn", "*"));
        PolicyDecision d = service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect");
        assertEquals("DENY", d.getDecision());
        assertEquals("CONFLICTING_POLICIES", d.getReason());
    }

    @Test
    void explicitDenyDenies() {
        assignAll(policy("DENY", "vpn", "connect"));
        assertEquals("EXPLICIT_DENY", service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect").getReason());
    }

    @Test
    void nonMatchingPoliciesDeny() {
        assignAll(policy("ALLOW", "siem", "read"));
        assertEquals("NO_MATCHING_POLICY", service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect").getReason());
    }

    @Test
    void wildcardPatternsMatch() {
        assignAll(policy("ALLOW", "vpn.*", "*"));
        assertEquals("ALLOW", service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn.gateway", "connect").getDecision());
    }

    @Test
    void repositoryFailureFailsClosed() {
        when(assignments.findByTenantIdAndSubjectTypeAndSubjectIdIn(any(), anyString(), anyCollection())).thenThrow(new IllegalStateException("db down"));
        PolicyDecision d = service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect");
        assertEquals("DENY", d.getDecision());
        assertEquals("EVALUATION_ERROR", d.getReason());
        verifyAudited("DENY");
    }

    @Test
    void auditFailureFailsClosedEvenWhenPolicyAllows() {
        assignAll(policy("ALLOW", "vpn", "connect"));
        doThrow(new IllegalStateException("audit store down")).when(audit)
                .recordIndependent(any(), any(), anyString(), anyString(), anyString(), anyString(), anyMap());
        PolicyDecision d = service(false).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect");
        assertEquals("DENY", d.getDecision());
        assertEquals("AUDIT_UNAVAILABLE", d.getReason());
    }

    @Test
    void invalidRequestDenies() {
        assertEquals("INVALID_REQUEST", service(false).evaluate(tenant, requester, "USER", "not-a-uuid", "vpn", "connect").getReason());
        assertEquals("INVALID_REQUEST", service(false).evaluate(tenant, requester, "USER", user.toString(), " ", "connect").getReason());
    }

    @Test
    void secDenialOverridesLocalAllow() {
        assignAll(policy("ALLOW", "vpn", "connect"));
        when(sec.isAllowed(any(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        assertEquals("SEC_DENIED", service(true).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect").getReason());
    }

    @Test
    void secOutageFailsClosed() {
        assignAll(policy("ALLOW", "vpn", "connect"));
        when(sec.isAllowed(any(), anyString(), anyString(), anyString(), anyString())).thenThrow(DcException.upstreamTimeout("sec down"));
        PolicyDecision d = service(true).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect");
        assertEquals("DENY", d.getDecision());
        assertEquals("SEC_UNAVAILABLE", d.getReason());
    }

    @Test
    void secAllowConfirmsLocalAllow() {
        assignAll(policy("ALLOW", "vpn", "connect"));
        when(sec.isAllowed(any(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        assertEquals("ALLOW", service(true).evaluate(tenant, requester, "USER", user.toString(), "vpn", "connect").getDecision());
    }
}
