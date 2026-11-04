package com.texora.secops.dc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dc_config_item", schema = "dc")
public class DcConfigItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "key", nullable = false, length = 255)
    private String configKey;

    @Column(name = "value", nullable = false, length = 4000)
    private String configValue;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "rejection_reason", nullable = true, length = 1000)
    private String rejectionReason;

    @Column(name = "activated_at", nullable = true)
    private Instant activatedAt;

    public DcConfigItem() {
        // required by JPA / Jackson
    }

    public DcConfigItem(UUID id, UUID tenantId, String configKey, String configValue, String status, UUID submittedBy, Instant submittedAt, Instant updatedAt, String rejectionReason, Instant activatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.configKey = configKey;
        this.configValue = configValue;
        this.status = status;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.updatedAt = updatedAt;
        this.rejectionReason = rejectionReason;
        this.activatedAt = activatedAt;
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

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DcConfigItem that = (DcConfigItem) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DcConfigItem{id=" + id + ", " + "configKey=" + configKey + ", " + "status=" + status + "}";
    }
}
