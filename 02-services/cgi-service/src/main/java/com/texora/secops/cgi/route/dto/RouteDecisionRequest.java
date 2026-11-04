package com.texora.secops.cgi.route.dto;

import com.texora.secops.cgi.route.domain.DecisionType;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * POST /api/v1/route-allowlist/{id}/decision request body.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class RouteDecisionRequest {

    @NotNull
    private DecisionType decision;

    public RouteDecisionRequest() {
        // default constructor (JSON / property binding)
    }

    public RouteDecisionRequest(DecisionType decision) {
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
        if (!(other instanceof RouteDecisionRequest)) {
            return false;
        }
        RouteDecisionRequest that = (RouteDecisionRequest) other;
        return Objects.equals(decision, that.decision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision);
    }

    @Override
    public String toString() {
        return "RouteDecisionRequest{" + "decision=" + decision + "}";
    }

}
