package com.texora.secops.dc.policy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public class PolicyAssignmentRequest {

    @NotNull
    private UUID policyId;

    @NotBlank
    @Pattern(regexp = "USER|GROUP|ROLE")
    private String subjectType;

    @NotBlank
    @Size(max = 255)
    private String subjectId;

    public PolicyAssignmentRequest() {
        // required by Jackson
    }

    public PolicyAssignmentRequest(UUID policyId, String subjectType, String subjectId) {
        this.policyId = policyId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PolicyAssignmentRequest that = (PolicyAssignmentRequest) other;
        return Objects.equals(policyId, that.policyId) && Objects.equals(subjectType, that.subjectType) && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId, subjectType, subjectId);
    }

    @Override
    public String toString() {
        return "PolicyAssignmentRequest{policyId=" + policyId + ", " + "subjectType=" + subjectType + ", " + "subjectId=" + subjectId + "}";
    }
}
