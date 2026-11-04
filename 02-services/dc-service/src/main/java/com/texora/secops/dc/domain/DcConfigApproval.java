package com.texora.secops.dc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dc_config_approval", schema = "dc")
public class DcConfigApproval {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "config_item_id", nullable = false, updatable = false)
    private UUID configItemId;

    @Column(name = "approver_id", nullable = false, updatable = false)
    private UUID approverId;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "reason", nullable = true, length = 1000)
    private String reason;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    public DcConfigApproval() {
        // required by JPA / Jackson
    }

    public DcConfigApproval(UUID id, UUID tenantId, UUID configItemId, UUID approverId, String decision, String reason, Instant decidedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.configItemId = configItemId;
        this.approverId = approverId;
        this.decision = decision;
        this.reason = reason;
        this.decidedAt = decidedAt;
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

    public UUID getConfigItemId() {
        return configItemId;
    }

    public void setConfigItemId(UUID configItemId) {
        this.configItemId = configItemId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public void setApproverId(UUID approverId) {
        this.approverId = approverId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DcConfigApproval that = (DcConfigApproval) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DcConfigApproval{id=" + id + ", " + "configItemId=" + configItemId + ", " + "decision=" + decision + "}";
    }
}
