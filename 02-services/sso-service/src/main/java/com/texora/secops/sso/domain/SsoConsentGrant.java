package com.texora.secops.sso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Per-user, per-application consent record. Missing/revoked consent must fail closed. */
@Entity
@Table(name = "sso_consent_grant", schema = "sso")
public class SsoConsentGrant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dc_user_id", nullable = false)
    private UUID dcUserId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "scopes_granted", nullable = false, columnDefinition = "text")
    private String scopesGranted;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SsoConsentGrant() {
        // required by JPA
    }

    public SsoConsentGrant(UUID id, UUID tenantId, UUID dcUserId, UUID applicationId,
                            String scopesGranted, Instant grantedAt, Instant revokedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.dcUserId = dcUserId;
        this.applicationId = applicationId;
        this.scopesGranted = scopesGranted;
        this.grantedAt = grantedAt;
        this.revokedAt = revokedAt;
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

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getScopesGranted() {
        return scopesGranted;
    }

    public void setScopesGranted(String scopesGranted) {
        this.scopesGranted = scopesGranted;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SsoConsentGrant)) {
            return false;
        }
        SsoConsentGrant that = (SsoConsentGrant) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SsoConsentGrant{id=" + id + ", dcUserId=" + dcUserId + ", applicationId=" + applicationId + "}";
    }
}
