package com.texora.secops.siem.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response body returned by SEC's policy evaluation endpoint. Internal to PolicyDecisionClient. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluatePolicyResponse {

    private String decision;
    private String reason;

    public EvaluatePolicyResponse() {
        // required for JSON deserialization
    }

    public EvaluatePolicyResponse(String decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    /** Fail-closed: only an exact "ALLOW" counts. Missing or unknown values mean not allowed. */
    @JsonIgnore
    public boolean isAllowed() {
        return "ALLOW".equals(decision);
    }
}
