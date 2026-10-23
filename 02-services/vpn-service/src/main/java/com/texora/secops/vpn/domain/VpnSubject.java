package com.texora.secops.vpn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * References dc_user.id by value only (no cross-schema FK).
 */
@Entity
@Table(name = "vpn_subject", schema = "vpn")
public class VpnSubject {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dc_user_id", nullable = false)
    private UUID dcUserId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "identity_synced_at", nullable = true)
    private Instant identitySyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VpnSubject() {
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

    public UUID getDcUserId() {
        return dcUserId;
    }

    public void setDcUserId(UUID dcUserId) {
        this.dcUserId = dcUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getIdentitySyncedAt() {
        return identitySyncedAt;
    }

    public void setIdentitySyncedAt(Instant identitySyncedAt) {
        this.identitySyncedAt = identitySyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VpnSubject)) {
            return false;
        }
        VpnSubject that = (VpnSubject) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "VpnSubject{" + "id=" + id + ", tenantId=" + tenantId + ", status=" + status + "}";
    }

}
