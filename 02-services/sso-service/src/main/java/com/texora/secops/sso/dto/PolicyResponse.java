package com.texora.secops.sso.dto;

import java.util.UUID;

/** Mirrors the shape returned by SEC's policy API for GET /api/v1/policies. */
public class PolicyResponse {

    private UUID applicationId;
    private String policyId;
    private boolean allowed;
    private String reason;

    public PolicyResponse() {
    }

    public PolicyResponse(UUID applicationId, String policyId, boolean allowed, String reason) {
        this.applicationId = applicationId;
        this.policyId = policyId;
        this.allowed = allowed;
        this.reason = reason;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
