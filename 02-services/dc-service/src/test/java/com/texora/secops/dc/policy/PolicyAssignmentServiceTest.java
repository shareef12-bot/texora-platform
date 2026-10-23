package com.texora.secops.dc.policy;

import com.texora.secops.dc.TestSupport;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcPolicy;
import com.texora.secops.dc.domain.DcPolicyAssignment;
import com.texora.secops.dc.domain.DcUser;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.policy.service.PolicyAssignmentService;
import com.texora.secops.dc.repository.GroupRepository;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.PolicyAssignmentRepository;
import com.texora.secops.dc.repository.PolicyRepository;
import com.texora.secops.dc.repository.UserRepository;
import com.texora.secops.dc.security.CallerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyAssignmentServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    private PolicyRepository policies;
    private PolicyAssignmentRepository assignments;
    private UserRepository users;
    private GroupRepository groups;
    private MembershipRepository memberships;
    private DcAuditRecorder audit;
    private PolicyAssignmentService service;
    private CallerContext caller;
    private DcPolicy userPolicy;

    @BeforeEach
    void setUp() {
        policies = mock(PolicyRepository.class);
        assignments = mock(PolicyAssignmentRepository.class);
        users = mock(UserRepository.class);
        groups = mock(GroupRepository.class);
        memberships = mock(MembershipRepository.class);
        audit = mock(DcAuditRecorder.class);
        service = new PolicyAssignmentService(policies, assignments, users, groups, memberships, audit,
                TestSupport.passThroughLookup(), TestSupport.fixedTime());
        caller = TestSupport.caller(tenant, admin, "dc.policy.assign", "policy-admin");
        userPolicy = new DcPolicy();
        userPolicy.setId(UUID.randomUUID());
        userPolicy.setSubjectType("USER");
        userPolicy.setEffect("ALLOW");
        when(policies.findByTenantIdAndId(tenant, userPolicy.getId())).thenReturn(Optional.of(userPolicy));
        when(users.findByTenantIdAndId(any(), any())).thenReturn(Optional.of(new DcUser()));
    }

    @Test
    void assignsPolicyAndAudits() {
        DcPolicyAssignment a = service.assign(caller, userPolicy.getId(), "USER", target.toString());
        assertEquals(target.toString(), a.getSubjectId());
        assertEquals(admin, a.getAssignedBy());
        verify(assignments).save(any(DcPolicyAssignment.class));
        verify(audit).record(eq(tenant), eq(admin), eq("POLICY_ASSIGNED"), anyString(), anyString(), eq("SUCCESS"), anyMap());
    }

    @Test
    void selfAssignmentIsBlockedAndAuditedIndependently() {
        DcException e = org.junit.jupiter.api.Assertions.assertThrows(DcException.class,
                () -> service.assign(caller, userPolicy.getId(), "USER", admin.toString()));
        assertEquals("PRIVILEGE_ESCALATION_BLOCKED", e.getCode());
        verify(assignments, never()).save(any());
        verify(audit).recordIndependent(eq(tenant), eq(admin), eq("POLICY_ASSIGNMENT_DENIED"), anyString(), anyString(), eq("DENIED"), anyMap());
    }

    @Test
    void assignmentToOwnGroupIsBlocked() {
        DcPolicy groupPolicy = new DcPolicy();
        groupPolicy.setId(UUID.randomUUID());
        groupPolicy.setSubjectType("GROUP");
        UUID groupId = UUID.randomUUID();
        when(policies.findByTenantIdAndId(tenant, groupPolicy.getId())).thenReturn(Optional.of(groupPolicy));
        when(groups.findByTenantIdAndId(tenant, groupId)).thenReturn(Optional.of(new DcGroup()));
        when(memberships.existsMembership(tenant, groupId, admin)).thenReturn(true);
        DcException e = org.junit.jupiter.api.Assertions.assertThrows(DcException.class,
                () -> service.assign(caller, groupPolicy.getId(), "GROUP", groupId.toString()));
        assertEquals("PRIVILEGE_ESCALATION_BLOCKED", e.getCode());
    }

    @Test
    void assignmentToOwnRoleIsBlocked() {
        DcPolicy rolePolicy = new DcPolicy();
        rolePolicy.setId(UUID.randomUUID());
        rolePolicy.setSubjectType("ROLE");
        when(policies.findByTenantIdAndId(tenant, rolePolicy.getId())).thenReturn(Optional.of(rolePolicy));
        DcException e = org.junit.jupiter.api.Assertions.assertThrows(DcException.class,
                () -> service.assign(caller, rolePolicy.getId(), "ROLE", "policy-admin"));
        assertEquals("PRIVILEGE_ESCALATION_BLOCKED", e.getCode());
    }

    @Test
    void subjectTypeMustMatchPolicy() {
        assertEquals("SUBJECT_TYPE_MISMATCH", org.junit.jupiter.api.Assertions.assertThrows(DcException.class,
                () -> service.assign(caller, userPolicy.getId(), "GROUP", UUID.randomUUID().toString())).getCode());
    }

    @Test
    void duplicateAssignmentConflicts() {
        when(assignments.existsByTenantIdAndPolicyIdAndSubjectTypeAndSubjectId(tenant, userPolicy.getId(), "USER", target.toString())).thenReturn(true);
        assertEquals(409, org.junit.jupiter.api.Assertions.assertThrows(DcException.class,
                () -> service.assign(caller, userPolicy.getId(), "USER", target.toString())).getStatus().value());
    }

    @Test
    void malformedSubjectIdIsUnprocessable() {
        assertEquals("INVALID_SUBJECT", org.junit.jupiter.api.Assertions.assertThrows(DcException.class,
                () -> service.assign(caller, userPolicy.getId(), "USER", "not-a-uuid")).getCode());
    }

    @Test
    void unknownPolicyIsNotFound() {
        assertEquals(404, org.junit.jupiter.api.Assertions.assertThrows(DcException.class,
                () -> service.assign(caller, UUID.randomUUID(), "USER", target.toString())).getStatus().value());
    }
}
