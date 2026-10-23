package com.texora.secops.siem.security;

/** Response body returned by SEC's policy evaluation endpoint. Internal to PolicyDecisionClient. */
public class EvaluatePolicyResponse {

    private boolean allowed;
    private String reason;

    public EvaluatePolicyResponse() {
        // required for JSON deserialization
    }

    public EvaluatePolicyResponse(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
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
