package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AccessPolicyResponse {

    private UUID id;

    private String name;

    private String status;

    private Map<String, Object> conditions;

    private UUID submittedBy;

    private Instant submittedAt;

    private UUID supersedesPolicyId;

    public AccessPolicyResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    public void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions;
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

    public UUID getSupersedesPolicyId() {
        return supersedesPolicyId;
    }

    public void setSupersedesPolicyId(UUID supersedesPolicyId) {
        this.supersedesPolicyId = supersedesPolicyId;
    }

    @Override
    public String toString() {
        return "AccessPolicyResponse{" + "id=" + id + ", name=" + name + ", status=" + status + ", conditions=" + conditions + ", submittedBy=" + submittedBy + ", submittedAt=" + submittedAt + ", supersedesPolicyId=" + supersedesPolicyId + "}";
    }

}
