package com.texora.secops.sec.policy.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for POST /api/v1/policies/evaluate.
 *
 * <p>decision is always one of: ALLOW | DENY</p>
 * <p>reason is one of: EXPLICIT_ALLOW | EXPLICIT_DENY | NO_MATCHING_POLICY | POLICY_CONFLICT | EVALUATION_ERROR</p>
 * <p>All four non-allow reasons yield decision=DENY (default deny).</p>
 */
public class PolicyEvaluationResponse {

    private String decision;
    private String reason;
    private UUID policyVersionId;
    private Instant evaluatedAt;

    public PolicyEvaluationResponse() {}

    public PolicyEvaluationResponse(String decision, String reason,
                                    UUID policyVersionId, Instant evaluatedAt) {
        this.decision = decision;
        this.reason = reason;
        this.policyVersionId = policyVersionId;
        this.evaluatedAt = evaluatedAt;
    }

    public static PolicyEvaluationResponse deny(String reason) {
        return new PolicyEvaluationResponse("DENY", reason, null, Instant.now());
    }

    public static PolicyEvaluationResponse allow(UUID policyVersionId) {
        return new PolicyEvaluationResponse("ALLOW", "EXPLICIT_ALLOW", policyVersionId, Instant.now());
    }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getPolicyVersionId() { return policyVersionId; }
    public void setPolicyVersionId(UUID policyVersionId) { this.policyVersionId = policyVersionId; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    @Override
    public String toString() {
        return "PolicyEvaluationResponse{decision='" + decision + "', reason='" + reason + "'}";
    }
}
