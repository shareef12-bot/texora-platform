package com.texora.secops.vpn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * mfaFactorRef is an opaque reference. No MFA seed/secret is ever stored here.
 */
@Entity
@Table(name = "vpn_device_enrollment", schema = "vpn")
public class DeviceEnrollment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vpn_subject_id", nullable = false)
    private UUID vpnSubjectId;

    @Column(name = "device_fingerprint", nullable = false, length = 255)
    private String deviceFingerprint;

    @Column(name = "mfa_factor_ref", nullable = false, length = 255)
    private String mfaFactorRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "enrolled_by", nullable = false)
    private UUID enrolledBy;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    public DeviceEnrollment() {
        // required by JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getVpnSubjectId() {
        return vpnSubjectId;
    }

    public void setVpnSubjectId(UUID vpnSubjectId) {
        this.vpnSubjectId = vpnSubjectId;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getMfaFactorRef() {
        return mfaFactorRef;
    }

    public void setMfaFactorRef(String mfaFactorRef) {
        this.mfaFactorRef = mfaFactorRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getEnrolledBy() {
        return enrolledBy;
    }

    public void setEnrolledBy(UUID enrolledBy) {
        this.enrolledBy = enrolledBy;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DeviceEnrollment)) {
            return false;
        }
        DeviceEnrollment that = (DeviceEnrollment) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DeviceEnrollment{" + "id=" + id + ", tenantId=" + tenantId + ", status=" + status + "}";
    }

}
