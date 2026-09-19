package com.texora.secops.sec.stub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/policies/evaluate}.
 *
 * <p>Contract matches SEC LLD §6.2.
 *
 * <pre>{@code
 * {
 *   "decision":    "PERMIT",        // PERMIT | DENY
 *   "reason":      "Policy P-42 matched",
 *   "policyId":    "uuid",          // which policy triggered the decision
 *   "evaluatedAt": "2026-09-15T10:00:00Z",
 *   "requestId":   "uuid"           // echo of a caller-supplied ID, or generated
 * }
 * }</pre>
 *
 * <p><strong>Fail-closed contract:</strong> any response that is not a 200 with
 * {@code "decision": "PERMIT"} must be treated as DENY by the calling service.
 * Never infer PERMIT from an error response.
 */
public class PolicyEvaluationResponse {

    public static final String DECISION_PERMIT = "PERMIT";
    public static final String DECISION_DENY   = "DENY";

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("policyId")
    private UUID policyId;

    @JsonProperty("evaluatedAt")
    private Instant evaluatedAt;

    @JsonProperty("requestId")
    private UUID requestId;

    public PolicyEvaluationResponse() {
        // Jackson
    }

    public PolicyEvaluationResponse(String decision, String reason,
                                     UUID policyId, Instant evaluatedAt,
                                     UUID requestId) {
        this.decision    = Objects.requireNonNull(decision,    "decision");
        this.reason      = reason;
        this.policyId    = policyId;
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.requestId   = requestId;
    }

    /** Factory: PERMIT response. */
    public static PolicyEvaluationResponse permit(String reason, UUID policyId, UUID requestId) {
        return new PolicyEvaluationResponse(DECISION_PERMIT, reason, policyId,
            Instant.now(), requestId);
    }

    /** Factory: DENY response. */
    public static PolicyEvaluationResponse deny(String reason, UUID requestId) {
        return new PolicyEvaluationResponse(DECISION_DENY, reason, null,
            Instant.now(), requestId);
    }

    public boolean isPermit() {
        return DECISION_PERMIT.equals(decision);
    }

    public String getDecision()             { return decision; }
    public void setDecision(String v)       { this.decision = v; }

    public String getReason()               { return reason; }
    public void setReason(String v)         { this.reason = v; }

    public UUID getPolicyId()               { return policyId; }
    public void setPolicyId(UUID v)         { this.policyId = v; }

    public Instant getEvaluatedAt()         { return evaluatedAt; }
    public void setEvaluatedAt(Instant v)   { this.evaluatedAt = v; }

    public UUID getRequestId()              { return requestId; }
    public void setRequestId(UUID v)        { this.requestId = v; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PolicyEvaluationResponse)) return false;
        PolicyEvaluationResponse that = (PolicyEvaluationResponse) other;
        return Objects.equals(decision,  that.decision)
            && Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, requestId);
    }

    @Override
    public String toString() {
        return "PolicyEvaluationResponse{decision='" + decision + "', reason='" + reason + "'}";
    }
}
