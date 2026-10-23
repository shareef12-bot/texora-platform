package com.texora.secops.vpn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vpn_policy_assignment", schema = "vpn")
public class PolicyAssignment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "access_policy_id", nullable = false)
    private UUID accessPolicyId;

    @Column(name = "vpn_subject_id", nullable = false)
    private UUID vpnSubjectId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public PolicyAssignment() {
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

    public UUID getAccessPolicyId() {
        return accessPolicyId;
    }

    public void setAccessPolicyId(UUID accessPolicyId) {
        this.accessPolicyId = accessPolicyId;
    }

    public UUID getVpnSubjectId() {
        return vpnSubjectId;
    }

    public void setVpnSubjectId(UUID vpnSubjectId) {
        this.vpnSubjectId = vpnSubjectId;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(UUID assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PolicyAssignment)) {
            return false;
        }
        PolicyAssignment that = (PolicyAssignment) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PolicyAssignment{" + "id=" + id + ", tenantId=" + tenantId + "}";
    }

}
