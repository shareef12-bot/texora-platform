package com.texora.secops.dc.admin.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class GroupMembershipResponse {

    private UUID groupId;

    private UUID userId;

    private UUID assignedBy;

    private Instant assignedAt;

    public GroupMembershipResponse() {
        // required by Jackson
    }

    public GroupMembershipResponse(UUID groupId, UUID userId, UUID assignedBy, Instant assignedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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
        GroupMembershipResponse that = (GroupMembershipResponse) other;
        return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId) && Objects.equals(assignedBy, that.assignedBy) && Objects.equals(assignedAt, that.assignedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId, assignedBy, assignedAt);
    }

    @Override
    public String toString() {
        return "GroupMembershipResponse{groupId=" + groupId + ", " + "userId=" + userId + ", " + "assignedBy=" + assignedBy + ", " + "assignedAt=" + assignedAt + "}";
    }
}
