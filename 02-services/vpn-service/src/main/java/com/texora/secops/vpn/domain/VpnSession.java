package com.texora.secops.vpn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Control-plane record of a session. The dataplane holds the real tunnel state.
 */
@Entity
@Table(name = "vpn_session", schema = "vpn")
public class VpnSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vpn_subject_id", nullable = false)
    private UUID vpnSubjectId;

    @Column(name = "access_policy_id", nullable = false)
    private UUID accessPolicyId;

    @Column(name = "device_enrollment_id", nullable = true)
    private UUID deviceEnrollmentId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "established_at", nullable = false)
    private Instant establishedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = true)
    private Instant revokedAt;

    @Column(name = "revocation_trigger", nullable = true, length = 48)
    private String revocationTrigger;

    @Column(name = "revocation_reason", nullable = true, length = 500)
    private String revocationReason;

    @Column(name = "last_validated_at", nullable = false)
    private Instant lastValidatedAt;

    @Column(name = "next_validation_at", nullable = false)
    private Instant nextValidationAt;

    public VpnSession() {
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

    public UUID getAccessPolicyId() {
        return accessPolicyId;
    }

    public void setAccessPolicyId(UUID accessPolicyId) {
        this.accessPolicyId = accessPolicyId;
    }

    public UUID getDeviceEnrollmentId() {
        return deviceEnrollmentId;
    }

    public void setDeviceEnrollmentId(UUID deviceEnrollmentId) {
        this.deviceEnrollmentId = deviceEnrollmentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getEstablishedAt() {
        return establishedAt;
    }

    public void setEstablishedAt(Instant establishedAt) {
        this.establishedAt = establishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevocationTrigger() {
        return revocationTrigger;
    }

    public void setRevocationTrigger(String revocationTrigger) {
        this.revocationTrigger = revocationTrigger;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Instant lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public Instant getNextValidationAt() {
        return nextValidationAt;
    }

    public void setNextValidationAt(Instant nextValidationAt) {
        this.nextValidationAt = nextValidationAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VpnSession)) {
            return false;
        }
        VpnSession that = (VpnSession) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "VpnSession{" + "id=" + id + ", tenantId=" + tenantId + ", status=" + status + "}";
    }

}
