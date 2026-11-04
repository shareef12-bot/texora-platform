package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class PolicyAssignmentRequest {

    @NotNull
    private UUID subjectId;

    public PolicyAssignmentRequest() {
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    @Override
    public String toString() {
        return "PolicyAssignmentRequest{" + "subjectId=" + subjectId + "}";
    }

}
