package com.texora.secops.sec.stub.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * A configurable rule that the SEC stub uses to return a pre-set decision
 * for a specific (resourceType, action) combination or subject.
 *
 * <p>Loaded at startup from {@code sec-stub-rules.yml} and also manageable
 * at runtime via the admin API on port 8081 (dev only).
 */
public class StubDecisionRule {

    /** Match on resource type (nullable = match any). */
    private String resourceType;

    /** Match on action verb (nullable = match any). */
    private String action;

    /** Match on subject ID (nullable = match any). */
    private UUID subjectId;

    /** The decision to return: PERMIT or DENY. */
    private String decision;

    /** Human-readable reason included in the response. */
    private String reason;

    /** The fake policy ID to include in PERMIT responses. */
    private UUID policyId;

    public StubDecisionRule() {
    }

    public StubDecisionRule(String resourceType, String action,
                             UUID subjectId, String decision,
                             String reason, UUID policyId) {
        this.resourceType = resourceType;
        this.action       = action;
        this.subjectId    = subjectId;
        this.decision     = decision;
        this.reason       = reason;
        this.policyId     = policyId;
    }

    /**
     * Returns true if this rule matches the given request.
     * A null field in the rule is a wildcard.
     */
    public boolean matches(PolicyEvaluationRequest request) {
        if (resourceType != null && !resourceType.equals(request.getResourceType())) return false;
        if (action       != null && !action.equals(request.getAction()))             return false;
        if (subjectId    != null && !subjectId.equals(request.getSubjectId()))       return false;
        return true;
    }

    public String getResourceType()          { return resourceType; }
    public void setResourceType(String v)    { this.resourceType = v; }

    public String getAction()                { return action; }
    public void setAction(String v)          { this.action = v; }

    public UUID getSubjectId()               { return subjectId; }
    public void setSubjectId(UUID v)         { this.subjectId = v; }

    public String getDecision()              { return decision; }
    public void setDecision(String v)        { this.decision = v; }

    public String getReason()                { return reason; }
    public void setReason(String v)          { this.reason = v; }

    public UUID getPolicyId()                { return policyId; }
    public void setPolicyId(UUID v)          { this.policyId = v; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StubDecisionRule)) return false;
        StubDecisionRule that = (StubDecisionRule) other;
        return Objects.equals(resourceType, that.resourceType)
            && Objects.equals(action, that.action)
            && Objects.equals(subjectId, that.subjectId)
            && Objects.equals(decision, that.decision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, action, subjectId, decision);
    }

    @Override
    public String toString() {
        return "StubDecisionRule{resourceType='" + resourceType
            + "', action='" + action + "', decision='" + decision + "'}";
    }
}
