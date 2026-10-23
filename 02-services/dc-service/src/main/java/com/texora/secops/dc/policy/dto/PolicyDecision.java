package com.texora.secops.dc.policy.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PolicyDecision {

    private String decision;

    private String reason;

    private List<UUID> matchedPolicyIds;

    public PolicyDecision() {
        // required by Jackson
    }

    public PolicyDecision(String decision, String reason, List<UUID> matchedPolicyIds) {
        this.decision = decision;
        this.reason = reason;
        this.matchedPolicyIds = matchedPolicyIds;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<UUID> getMatchedPolicyIds() {
        return matchedPolicyIds;
    }

    public void setMatchedPolicyIds(List<UUID> matchedPolicyIds) {
        this.matchedPolicyIds = matchedPolicyIds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PolicyDecision that = (PolicyDecision) other;
        return Objects.equals(decision, that.decision) && Objects.equals(reason, that.reason) && Objects.equals(matchedPolicyIds, that.matchedPolicyIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, reason, matchedPolicyIds);
    }

    @Override
    public String toString() {
        return "PolicyDecision{decision=" + decision + ", " + "reason=" + reason + ", " + "matchedPolicyIds=" + matchedPolicyIds + "}";
    }
}
