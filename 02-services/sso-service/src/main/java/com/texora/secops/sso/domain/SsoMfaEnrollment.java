package com.texora.secops.sso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Per-user MFA factor enrollment state, checked at login (§5.3). */
@Entity
@Table(name = "sso_mfa_enrollment", schema = "sso")
public class SsoMfaEnrollment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dc_user_id", nullable = false)
    private UUID dcUserId;

    @Column(name = "factor_type", nullable = false, length = 32)
    private String factorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MfaFactorStatus status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    protected SsoMfaEnrollment() {
        // required by JPA
    }

    public SsoMfaEnrollment(UUID id, UUID tenantId, UUID dcUserId, String factorType,
                             MfaFactorStatus status, Instant enrolledAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.dcUserId = dcUserId;
        this.factorType = factorType;
        this.status = status;
        this.enrolledAt = enrolledAt;
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

    public UUID getDcUserId() {
        return dcUserId;
    }

    public void setDcUserId(UUID dcUserId) {
        this.dcUserId = dcUserId;
    }

    public String getFactorType() {
        return factorType;
    }

    public void setFactorType(String factorType) {
        this.factorType = factorType;
    }

    public MfaFactorStatus getStatus() {
        return status;
    }

    public void setStatus(MfaFactorStatus status) {
        this.status = status;
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
        if (!(other instanceof SsoMfaEnrollment)) {
            return false;
        }
        SsoMfaEnrollment that = (SsoMfaEnrollment) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SsoMfaEnrollment{id=" + id + ", dcUserId=" + dcUserId + ", factorType='" + factorType
                + "', status=" + status + "}";
    }
}
