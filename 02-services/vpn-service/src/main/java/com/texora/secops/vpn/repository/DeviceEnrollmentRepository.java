package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.DeviceEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceEnrollmentRepository extends JpaRepository<DeviceEnrollment, UUID> {

    Optional<DeviceEnrollment> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DeviceEnrollment> findByTenantIdAndVpnSubjectIdAndDeviceFingerprint(UUID tenantId, UUID vpnSubjectId, String deviceFingerprint);

    List<DeviceEnrollment> findByTenantIdAndVpnSubjectId(UUID tenantId, UUID vpnSubjectId);
}
