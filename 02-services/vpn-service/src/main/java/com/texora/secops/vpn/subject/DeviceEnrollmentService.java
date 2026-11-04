package com.texora.secops.vpn.subject;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.DeviceEnrollment;
import com.texora.secops.vpn.dto.DeviceEnrollmentRequest;
import com.texora.secops.vpn.dto.DeviceEnrollmentResponse;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.DeviceEnrollmentRepository;
import com.texora.secops.vpn.repository.VpnSubjectRepository;
import com.texora.secops.vpn.security.Caller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Device enrollment and MFA factor REFERENCES used as policy inputs. No MFA seed or secret is ever stored. */
@Service
public class DeviceEnrollmentService {

    private final DeviceEnrollmentRepository devices;
    private final VpnSubjectRepository subjects;
    private final VpnAuditRecorder audit;
    private final Clock clock;

    public DeviceEnrollmentService(DeviceEnrollmentRepository devices, VpnSubjectRepository subjects,
                                   VpnAuditRecorder audit, Clock clock) {
        this.devices = devices;
        this.subjects = subjects;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public DeviceEnrollmentResponse enroll(Caller caller, UUID subjectId, DeviceEnrollmentRequest request) {
        UUID tenantId = caller.getTenantId();
        requireSubject(tenantId, subjectId);
        if (devices.findByTenantIdAndVpnSubjectIdAndDeviceFingerprint(tenantId, subjectId, request.getDeviceFingerprint()).isPresent()) {
            throw VpnException.conflict("DEVICE_EXISTS", "Device is already enrolled for this subject");
        }
        DeviceEnrollment d = new DeviceEnrollment();
        d.setId(UUID.randomUUID());
        d.setTenantId(tenantId);
        d.setVpnSubjectId(subjectId);
        d.setDeviceFingerprint(request.getDeviceFingerprint());
        d.setMfaFactorRef(request.getMfaFactorRef());
        d.setStatus(VpnConstants.DEVICE_ACTIVE);
        d.setEnrolledBy(caller.getUserId());
        d.setEnrolledAt(clock.instant());
        devices.save(d);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectId", subjectId.toString());
        payload.put("deviceId", d.getId().toString());
        audit.record(tenantId, caller.getUserId(), VpnConstants.ACTION_DEVICE_ENROLLED, "VPN_SUBJECT",
                subjectId.toString(), VpnConstants.OUTCOME_SUCCESS, payload);
        return toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<DeviceEnrollmentResponse> list(UUID tenantId, UUID subjectId) {
        requireSubject(tenantId, subjectId);
        List<DeviceEnrollmentResponse> out = new ArrayList<>();
        for (DeviceEnrollment d : devices.findByTenantIdAndVpnSubjectId(tenantId, subjectId)) {
            out.add(toResponse(d));
        }
        return out;
    }

    /** The ACTIVE enrollment for this subject+fingerprint, if any. */
    @Transactional(readOnly = true)
    public Optional<DeviceEnrollment> findActive(UUID tenantId, UUID subjectId, String fingerprint) {
        return devices.findByTenantIdAndVpnSubjectIdAndDeviceFingerprint(tenantId, subjectId, fingerprint)
                .filter(d -> VpnConstants.DEVICE_ACTIVE.equals(d.getStatus()));
    }

    @Transactional(readOnly = true)
    public Optional<DeviceEnrollment> findActiveById(UUID tenantId, UUID deviceId) {
        return devices.findByIdAndTenantId(deviceId, tenantId).filter(d -> VpnConstants.DEVICE_ACTIVE.equals(d.getStatus()));
    }

    private void requireSubject(UUID tenantId, UUID subjectId) {
        if (subjects.findByIdAndTenantId(subjectId, tenantId).isEmpty()) {
            if (subjects.existsByIdAndTenantIdNot(subjectId, tenantId)) {
                throw VpnException.forbidden("TENANT_FORBIDDEN", "Resource belongs to another tenant");
            }
            throw VpnException.notFound("SUBJECT_NOT_FOUND", "Subject not found");
        }
    }

    private DeviceEnrollmentResponse toResponse(DeviceEnrollment d) {
        DeviceEnrollmentResponse r = new DeviceEnrollmentResponse();
        r.setId(d.getId());
        r.setSubjectId(d.getVpnSubjectId());
        r.setDeviceFingerprint(d.getDeviceFingerprint());
        r.setMfaFactorRef(d.getMfaFactorRef());
        r.setStatus(d.getStatus());
        r.setEnrolledAt(d.getEnrolledAt());
        return r;
    }
}
