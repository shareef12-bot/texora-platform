package com.texora.secops.dc.policy.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PolicyAssignmentResponse {

    private UUID id;

    private UUID policyId;

    private String subjectType;

    private String subjectId;

    private UUID assignedBy;

    private Instant assignedAt;

    public PolicyAssignmentResponse() {
        // required by Jackson
    }

    public PolicyAssignmentResponse(UUID id, UUID policyId, String subjectType, String subjectId, UUID assignedBy, Instant assignedAt) {
        this.id = id;
        this.policyId = policyId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
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
        PolicyAssignmentResponse that = (PolicyAssignmentResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(policyId, that.policyId) && Objects.equals(subjectType, that.subjectType) && Objects.equals(subjectId, that.subjectId) && Objects.equals(assignedBy, that.assignedBy) && Objects.equals(assignedAt, that.assignedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, policyId, subjectType, subjectId, assignedBy, assignedAt);
    }

    @Override
    public String toString() {
        return "PolicyAssignmentResponse{id=" + id + ", " + "policyId=" + policyId + ", " + "subjectType=" + subjectType + ", " + "subjectId=" + subjectId + ", " + "assignedBy=" + assignedBy + ", " + "assignedAt=" + assignedAt + "}";
    }
}
