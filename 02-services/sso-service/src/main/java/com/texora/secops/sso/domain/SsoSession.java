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

/**
 * A user login session against a registered application.
 * dc_user_id references the user by VALUE ONLY — no cross-schema foreign key.
 * DC remains the system of record for identity.
 */
@Entity
@Table(name = "sso_session", schema = "sso")
public class SsoSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dc_user_id", nullable = false)
    private UUID dcUserId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SsoSession() {
        // required by JPA
    }

    public SsoSession(UUID id, UUID tenantId, UUID dcUserId, UUID applicationId,
                       SessionStatus status, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.dcUserId = dcUserId;
        this.applicationId = applicationId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
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

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SsoSession)) {
            return false;
        }
        SsoSession that = (SsoSession) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SsoSession{id=" + id + ", dcUserId=" + dcUserId + ", applicationId=" + applicationId
                + ", status=" + status + "}";
    }
}
