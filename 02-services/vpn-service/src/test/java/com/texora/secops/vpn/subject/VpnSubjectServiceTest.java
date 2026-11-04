package com.texora.secops.vpn.subject;

import com.texora.secops.vpn.TestTransactions;
import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.admin.VpnConfigService;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.VpnSubject;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import com.texora.secops.vpn.repository.VpnSubjectRepository;
import com.texora.secops.vpn.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VpnSubjectServiceTest {

    private final VpnSubjectRepository subjects = mock(VpnSubjectRepository.class);
    private final VpnSessionRepository sessions = mock(VpnSessionRepository.class);
    private final DcIdentityClient dc = mock(DcIdentityClient.class);
    private final VpnConfigService config = mock(VpnConfigService.class);
    private final VpnAuditRecorder audit = mock(VpnAuditRecorder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
    private VpnSubjectService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID dcUser = UUID.randomUUID();
    private VpnSubject subject;

    @BeforeEach
    void setUp() {
        service = new VpnSubjectService(subjects, sessions, dc, config, audit, TestTransactions.template(), clock);
        when(config.identityStalenessSeconds(tenant)).thenReturn(300);
        subject = new VpnSubject();
        subject.setId(UUID.randomUUID());
        subject.setTenantId(tenant);
        subject.setDcUserId(dcUser);
        subject.setStatus(VpnConstants.SUBJECT_ACTIVE);
        subject.setIdentitySyncedAt(clock.instant());
        when(subjects.findByIdAndTenantId(subject.getId(), tenant)).thenReturn(Optional.of(subject));
        when(subjects.save(any(VpnSubject.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void makeStale() {
        subject.setIdentitySyncedAt(clock.instant().minusSeconds(301));
    }

    @Test
    void freshActiveIdentityDoesNotCallDc() {
        assertThat(service.ensureFresh(tenant, subject.getId())).isEqualTo(IdentityState.ACTIVE);
        verify(dc, never()).lookup(any(), any());
    }

    @Test
    void freshButSuspendedIsInactiveWithoutCallingDc() {
        subject.setStatus(VpnConstants.SUBJECT_SUSPENDED);
        assertThat(service.ensureFresh(tenant, subject.getId())).isEqualTo(IdentityState.INACTIVE);
        verify(dc, never()).lookup(any(), any());
    }

    @Test
    void staleIdentityThatCannotBeRefreshedIsUncertainAndStatusIsUntouched() {
        makeStale();
        when(dc.lookup(tenant, dcUser)).thenReturn(DcIdentityResult.UNAVAILABLE);
        assertThat(service.ensureFresh(tenant, subject.getId())).isEqualTo(IdentityState.UNCERTAIN);
        assertThat(subject.getStatus()).isEqualTo(VpnConstants.SUBJECT_ACTIVE);
        verify(subjects, never()).save(any(VpnSubject.class));
    }

    @Test
    void staleIdentityThatDcSaysIsInactiveSuspendsAndMarksSessionsDue() {
        makeStale();
        when(dc.lookup(tenant, dcUser)).thenReturn(DcIdentityResult.INACTIVE);
        assertThat(service.ensureFresh(tenant, subject.getId())).isEqualTo(IdentityState.INACTIVE);
        assertThat(subject.getStatus()).isEqualTo(VpnConstants.SUBJECT_SUSPENDED);
        assertThat(subject.getIdentitySyncedAt()).isEqualTo(clock.instant());
        verify(sessions).markDueBySubject(tenant, subject.getId(), clock.instant());
    }

    @Test
    void userDeletedInDcIsTreatedAsInactive() {
        makeStale();
        when(dc.lookup(tenant, dcUser)).thenReturn(DcIdentityResult.NOT_FOUND);
        assertThat(service.ensureFresh(tenant, subject.getId())).isEqualTo(IdentityState.INACTIVE);
    }

    @Test
    void staleSuspendedSubjectReactivatedByDcIsActive() {
        subject.setStatus(VpnConstants.SUBJECT_SUSPENDED);
        makeStale();
        when(dc.lookup(tenant, dcUser)).thenReturn(DcIdentityResult.ACTIVE);
        assertThat(service.ensureFresh(tenant, subject.getId())).isEqualTo(IdentityState.ACTIVE);
        assertThat(subject.getStatus()).isEqualTo(VpnConstants.SUBJECT_ACTIVE);
    }

    @Test
    void unchangedStatusAfterRefreshDoesNotForceRevalidation() {
        makeStale();
        when(dc.lookup(tenant, dcUser)).thenReturn(DcIdentityResult.ACTIVE);
        service.ensureFresh(tenant, subject.getId());
        verify(sessions, never()).markDueBySubject(any(), any(), any());
    }

    @Test
    void unknownSubjectIsInactive() {
        assertThat(service.ensureFresh(tenant, UUID.randomUUID())).isEqualTo(IdentityState.INACTIVE);
    }

    @Test
    void createRules() {
        Caller caller = new Caller(tenant, UUID.randomUUID());
        UUID newUser = UUID.randomUUID();
        when(subjects.findByTenantIdAndDcUserId(tenant, newUser)).thenReturn(Optional.empty());

        when(dc.lookup(tenant, newUser)).thenReturn(DcIdentityResult.UNAVAILABLE);
        assertThatThrownBy(() -> service.create(caller, newUser))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(504));
        when(dc.lookup(tenant, newUser)).thenReturn(DcIdentityResult.INACTIVE);
        assertThatThrownBy(() -> service.create(caller, newUser))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(422));
        when(dc.lookup(tenant, newUser)).thenReturn(DcIdentityResult.NOT_FOUND);
        assertThatThrownBy(() -> service.create(caller, newUser)).isInstanceOf(VpnException.class);

        when(dc.lookup(tenant, newUser)).thenReturn(DcIdentityResult.ACTIVE);
        var created = service.create(caller, newUser);
        assertThat(created.getStatus()).isEqualTo(VpnConstants.SUBJECT_ACTIVE);
        verify(audit).record(any(), any(), any(), any(), any(), any(), any());

        when(subjects.findByTenantIdAndDcUserId(tenant, dcUser)).thenReturn(Optional.of(subject));
        assertThatThrownBy(() -> service.create(caller, dcUser))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void lookupsDelegate() {
        when(subjects.findByTenantIdAndDcUserId(tenant, dcUser)).thenReturn(Optional.of(subject));
        assertThat(service.findByDcUser(tenant, dcUser)).contains(subject);
        assertThat(service.findDcUserId(tenant, subject.getId())).contains(dcUser);
    }
}
