package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class PolicyDecisionResponse {

    private UUID policyId;

    private String status;

    private UUID approverId;

    private Instant decidedAt;

    public PolicyDecisionResponse() {
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public void setApproverId(UUID approverId) {
        this.approverId = approverId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    @Override
    public String toString() {
        return "PolicyDecisionResponse{" + "policyId=" + policyId + ", status=" + status + ", approverId=" + approverId + ", decidedAt=" + decidedAt + "}";
    }

}
