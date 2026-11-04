package com.texora.secops.dc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dc_group_membership", schema = "dc")
public class DcGroupMembership {

    @EmbeddedId
    private DcGroupMembershipId id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    public DcGroupMembership() {
        // required by JPA
    }

    public DcGroupMembership(DcGroupMembershipId id, UUID tenantId, UUID assignedBy, Instant assignedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public DcGroupMembershipId getId() {
        return id;
    }

    public void setId(DcGroupMembershipId id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DcGroupMembership that = (DcGroupMembership) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DcGroupMembership{id=" + id + "}";
    }
}
