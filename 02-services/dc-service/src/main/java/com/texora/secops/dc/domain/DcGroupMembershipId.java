package com.texora.secops.dc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite PK (user_id, group_id) of dc_group_membership. */
@Embeddable
public class DcGroupMembershipId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    public DcGroupMembershipId() {
        // required by JPA
    }

    public DcGroupMembershipId(UUID userId, UUID groupId) {
        this.userId = userId;
        this.groupId = groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DcGroupMembershipId that = (DcGroupMembershipId) other;
        return Objects.equals(userId, that.userId) && Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, groupId);
    }

    @Override
    public String toString() {
        return "DcGroupMembershipId{userId=" + userId + ", groupId=" + groupId + "}";
    }
}
