package com.texora.secops.cgi.execution.dto;

import com.texora.secops.cgi.route.domain.DecisionType;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * POST /api/v1/worker-isolation/{id}/decision request body.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class IsolationDecisionRequest {

    @NotNull
    private DecisionType decision;

    public IsolationDecisionRequest() {
        // default constructor (JSON / property binding)
    }

    public IsolationDecisionRequest(DecisionType decision) {
        this.decision = decision;
    }

    public DecisionType getDecision() {
        return decision;
    }

    public void setDecision(DecisionType decision) {
        this.decision = decision;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IsolationDecisionRequest)) {
            return false;
        }
        IsolationDecisionRequest that = (IsolationDecisionRequest) other;
        return Objects.equals(decision, that.decision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision);
    }

    @Override
    public String toString() {
        return "IsolationDecisionRequest{" + "decision=" + decision + "}";
    }

}
