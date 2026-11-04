package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class PolicyAssignmentResponse {

    private UUID id;

    private UUID accessPolicyId;

    private UUID subjectId;

    private UUID assignedBy;

    private Instant assignedAt;

    public PolicyAssignmentResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccessPolicyId() {
        return accessPolicyId;
    }

    public void setAccessPolicyId(UUID accessPolicyId) {
        this.accessPolicyId = accessPolicyId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
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
    public String toString() {
        return "PolicyAssignmentResponse{" + "id=" + id + ", accessPolicyId=" + accessPolicyId + ", subjectId=" + subjectId + ", assignedBy=" + assignedBy + ", assignedAt=" + assignedAt + "}";
    }

}
