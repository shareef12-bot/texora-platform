package com.texora.secops.vpn.subject;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.DeviceEnrollment;
import com.texora.secops.vpn.domain.VpnSubject;
import com.texora.secops.vpn.dto.DeviceEnrollmentRequest;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.DeviceEnrollmentRepository;
import com.texora.secops.vpn.repository.VpnSubjectRepository;
import com.texora.secops.vpn.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceEnrollmentServiceTest {

    private final DeviceEnrollmentRepository devices = mock(DeviceEnrollmentRepository.class);
    private final VpnSubjectRepository subjects = mock(VpnSubjectRepository.class);
    private final VpnAuditRecorder audit = mock(VpnAuditRecorder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
    private final DeviceEnrollmentService service = new DeviceEnrollmentService(devices, subjects, audit, clock);
    private final UUID tenant = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final Caller caller = new Caller(tenant, UUID.randomUUID());

    @BeforeEach
    void setUp() {
        when(subjects.findByIdAndTenantId(subjectId, tenant)).thenReturn(Optional.of(new VpnSubject()));
        when(devices.save(any(DeviceEnrollment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private DeviceEnrollmentRequest req() {
        DeviceEnrollmentRequest r = new DeviceEnrollmentRequest();
        r.setDeviceFingerprint("fp-1");
        r.setMfaFactorRef("factor-ref-1");
        return r;
    }

    @Test
    void enrollStoresOnlyReferencesAndAudits() {
        when(devices.findByTenantIdAndVpnSubjectIdAndDeviceFingerprint(tenant, subjectId, "fp-1")).thenReturn(Optional.empty());
        var r = service.enroll(caller, subjectId, req());
        assertThat(r.getStatus()).isEqualTo(VpnConstants.DEVICE_ACTIVE);
        assertThat(r.getMfaFactorRef()).isEqualTo("factor-ref-1");
        verify(audit).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void duplicateIs409() {
        when(devices.findByTenantIdAndVpnSubjectIdAndDeviceFingerprint(tenant, subjectId, "fp-1"))
                .thenReturn(Optional.of(new DeviceEnrollment()));
        assertThatThrownBy(() -> service.enroll(caller, subjectId, req()))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void subjectInAnotherTenantIs403AndUnknownIs404() {
        UUID foreign = UUID.randomUUID();
        when(subjects.findByIdAndTenantId(foreign, tenant)).thenReturn(Optional.empty());
        when(subjects.existsByIdAndTenantIdNot(foreign, tenant)).thenReturn(true);
        assertThatThrownBy(() -> service.enroll(caller, foreign, req()))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        assertThatThrownBy(() -> service.list(tenant, foreign)).isInstanceOf(VpnException.class);
        UUID missing = UUID.randomUUID();
        when(subjects.findByIdAndTenantId(missing, tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(tenant, missing))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void onlyActiveDevicesAreFoundForPolicyDecisions() {
        DeviceEnrollment active = new DeviceEnrollment();
        active.setStatus(VpnConstants.DEVICE_ACTIVE);
        DeviceEnrollment revoked = new DeviceEnrollment();
        revoked.setStatus("REVOKED");
        UUID id = UUID.randomUUID();
        when(devices.findByTenantIdAndVpnSubjectIdAndDeviceFingerprint(tenant, subjectId, "a")).thenReturn(Optional.of(active));
        when(devices.findByTenantIdAndVpnSubjectIdAndDeviceFingerprint(tenant, subjectId, "r")).thenReturn(Optional.of(revoked));
        when(devices.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(revoked));
        assertThat(service.findActive(tenant, subjectId, "a")).isPresent();
        assertThat(service.findActive(tenant, subjectId, "r")).isEmpty();
        assertThat(service.findActiveById(tenant, id)).isEmpty();
        when(devices.findByTenantIdAndVpnSubjectId(tenant, subjectId)).thenReturn(List.of(active));
        assertThat(service.list(tenant, subjectId)).hasSize(1);
    }
}
