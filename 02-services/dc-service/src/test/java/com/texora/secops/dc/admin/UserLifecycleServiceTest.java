package com.texora.secops.dc.admin;

import com.texora.secops.dc.TestSupport;
import com.texora.secops.dc.adapter.HrmsEmployee;
import com.texora.secops.dc.adapter.HrmsEmployeeAdapter;
import com.texora.secops.dc.admin.service.UserLifecycleService;
import com.texora.secops.dc.admin.service.UserLifecycleService.ApplyResult;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcUser;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.repository.GroupRepository;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.UserRepository;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.sync.service.DirectorySyncOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserLifecycleServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final Instant t1 = Instant.parse("2026-02-01T00:00:00Z");
    private final Instant t2 = Instant.parse("2026-03-01T00:00:00Z");

    private UserRepository users;
    private GroupRepository groups;
    private HrmsEmployeeAdapter hrms;
    private DirectorySyncOrchestrator sync;
    private DcAuditRecorder audit;
    private UserLifecycleService service;
    private CallerContext caller;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        groups = mock(GroupRepository.class);
        hrms = mock(HrmsEmployeeAdapter.class);
        sync = mock(DirectorySyncOrchestrator.class);
        audit = mock(DcAuditRecorder.class);
        service = new UserLifecycleService(users, groups, mock(MembershipRepository.class), hrms, sync, audit,
                TestSupport.passThroughLookup(), TestSupport.fixedTime());
        caller = TestSupport.caller(tenant, admin, "dc.user.write");
    }

    private HrmsEmployee emp(String status, Instant modified) {
        return new HrmsEmployee("EMP-1", "jsmith", "Jane Smith", "eng", status, modified);
    }

    private DcUser existing(Instant hrmsModified) {
        DcUser u = new DcUser();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenant);
        u.setUsername("jsmith");
        u.setDisplayName("Jane Smith");
        u.setOrgUnitId("eng");
        u.setStatus("ACTIVE");
        u.setHrmsEmployeeId("EMP-1");
        u.setHrmsLastModified(hrmsModified);
        return u;
    }

    @Test
    void materializeProjectsEmployeeAuditsAndRequestsSync() {
        when(hrms.fetchEmployee(tenant, "EMP-1")).thenReturn(Optional.of(emp("ACTIVE", t1)));
        DcUser u = service.materialize(caller, "EMP-1", null);
        assertEquals("jsmith", u.getUsername());
        assertEquals("EMP-1", u.getHrmsEmployeeId());
        assertFalse(u.isSecurityLocked());
        verify(audit).record(eq(tenant), eq(admin), eq("USER_MATERIALIZED"), anyString(), anyString(), eq("SUCCESS"), anyMap());
        verify(sync).requestSync(eq(tenant), eq("USER"), anyString(), eq("UPSERT"), anyString(), eq(admin), anyMap());
    }

    @Test
    void materializeWithInitialGroupsAddsMembership() {
        DcGroup g = new DcGroup();
        g.setId(UUID.randomUUID());
        g.setName("vpn-admins");
        when(groups.findByTenantIdAndName(tenant, "vpn-admins")).thenReturn(Optional.of(g));
        when(hrms.fetchEmployee(tenant, "EMP-1")).thenReturn(Optional.of(emp("ACTIVE", t1)));
        service.materialize(caller, "EMP-1", Collections.singletonList("vpn-admins"));
        verify(audit).record(eq(tenant), eq(admin), eq("GROUP_MEMBER_ADDED"), anyString(), anyString(), eq("SUCCESS"), anyMap());
    }

    @Test
    void unknownInitialGroupIsUnprocessable() {
        when(hrms.fetchEmployee(tenant, "EMP-1")).thenReturn(Optional.of(emp("ACTIVE", t1)));
        assertEquals("GROUP_NOT_FOUND", assertThrows(DcException.class,
                () -> service.materialize(caller, "EMP-1", Collections.singletonList("nope"))).getCode());
        verify(users, never()).save(any());
    }

    @Test
    void employeeMissingInHrmsIs422() {
        when(hrms.fetchEmployee(tenant, "EMP-9")).thenReturn(Optional.<HrmsEmployee>empty());
        assertEquals("HRMS_EMPLOYEE_NOT_FOUND", assertThrows(DcException.class, () -> service.materialize(caller, "EMP-9", null)).getCode());
    }

    @Test
    void hrmsOutageIs504() {
        when(hrms.fetchEmployee(any(), anyString())).thenThrow(new IllegalStateException("down"));
        assertEquals(504, assertThrows(DcException.class, () -> service.materialize(caller, "EMP-1", null)).getStatus().value());
    }

    @Test
    void alreadyProjectedEmployeeConflicts() {
        when(hrms.fetchEmployee(tenant, "EMP-1")).thenReturn(Optional.of(emp("ACTIVE", t1)));
        when(users.findByTenantIdAndHrmsEmployeeId(tenant, "EMP-1")).thenReturn(Optional.of(existing(t1)));
        assertEquals("USER_EXISTS", assertThrows(DcException.class, () -> service.materialize(caller, "EMP-1", null)).getCode());
    }

    @Test
    void usernameClashConflicts() {
        when(hrms.fetchEmployee(tenant, "EMP-1")).thenReturn(Optional.of(emp("ACTIVE", t1)));
        when(users.findByTenantIdAndUsername(tenant, "jsmith")).thenReturn(Optional.of(existing(null)));
        assertEquals("USERNAME_EXISTS", assertThrows(DcException.class, () -> service.materialize(caller, "EMP-1", null)).getCode());
    }

    @Test
    void applyCreatesNewEmployee() {
        assertEquals(ApplyResult.CREATED, service.applyHrmsSnapshot(tenant, admin, emp("ACTIVE", t1)));
        verify(sync).requestSync(eq(tenant), eq("USER"), anyString(), eq("UPSERT"), anyString(), eq(admin), anyMap());
    }

    @Test
    void staleOrReplayedSnapshotIsIgnored() {
        when(users.findByTenantIdAndHrmsEmployeeId(tenant, "EMP-1")).thenReturn(Optional.of(existing(t2)));
        assertEquals(ApplyResult.SKIPPED_STALE, service.applyHrmsSnapshot(tenant, admin, emp("TERMINATED", t1)));
        assertEquals(ApplyResult.SKIPPED_STALE, service.applyHrmsSnapshot(tenant, admin, emp("TERMINATED", t2)));
        verify(sync, never()).requestSync(any(), anyString(), anyString(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void newerIdenticalSnapshotOnlyAdvancesWatermark() {
        DcUser u = existing(t1);
        when(users.findByTenantIdAndHrmsEmployeeId(tenant, "EMP-1")).thenReturn(Optional.of(u));
        assertEquals(ApplyResult.UNCHANGED, service.applyHrmsSnapshot(tenant, admin, emp("ACTIVE", t2)));
        assertEquals(t2, u.getHrmsLastModified());
        verify(sync, never()).requestSync(any(), anyString(), anyString(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void terminationPropagatesToDirectoryAsDisabled() {
        DcUser u = existing(t1);
        when(users.findByTenantIdAndHrmsEmployeeId(tenant, "EMP-1")).thenReturn(Optional.of(u));
        assertEquals(ApplyResult.UPDATED, service.applyHrmsSnapshot(tenant, admin, emp("TERMINATED", t2)));
        assertEquals("TERMINATED", u.getStatus());
        verify(sync).requestSync(eq(tenant), eq("USER"), anyString(), eq("UPSERT"), anyString(), eq(admin), anyMap());
    }

    @Test
    void usernameChangeOntoAnotherUsersNameConflicts() {
        DcUser u = existing(t1);
        DcUser other = existing(null);
        other.setUsername("jdoe");
        when(users.findByTenantIdAndHrmsEmployeeId(tenant, "EMP-1")).thenReturn(Optional.of(u));
        when(users.findByTenantIdAndUsername(tenant, "jdoe")).thenReturn(Optional.of(other));
        HrmsEmployee renamed = new HrmsEmployee("EMP-1", "jdoe", "Jane Smith", "eng", "ACTIVE", t2);
        assertEquals("USERNAME_EXISTS", assertThrows(DcException.class, () -> service.applyHrmsSnapshot(tenant, admin, renamed)).getCode());
    }

    @Test
    void securityLockIsDcOwnedAndSyncs() {
        DcUser u = existing(t1);
        when(users.findByTenantIdAndId(tenant, u.getId())).thenReturn(Optional.of(u));
        DcUser result = service.setSecurityLock(caller, u.getId(), true);
        assertTrue(result.isSecurityLocked());
        verify(audit).record(eq(tenant), eq(admin), eq("USER_SECURITY_LOCKED"), anyString(), anyString(), eq("SUCCESS"), anyMap());
        verify(sync).requestSync(eq(tenant), eq("USER"), anyString(), eq("UPSERT"), anyString(), eq(admin), anyMap());
    }

    @Test
    void settingSameLockStateIsNoOp() {
        DcUser u = existing(t1);
        when(users.findByTenantIdAndId(tenant, u.getId())).thenReturn(Optional.of(u));
        service.setSecurityLock(caller, u.getId(), false);
        verify(audit, never()).record(any(), any(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }
}
