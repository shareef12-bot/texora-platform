package com.texora.secops.siem.security;

/**
 * ALLOW or DENY, plus the reason. Every constructor of this class outside
 * {@link #allow(String)} (i.e. every static factory used for a failure path) produces DENY -
 * there is deliberately no way to default to ALLOW (B.6.3: fail closed).
 */
public final class PolicyDecision {

    public enum Outcome {
        ALLOW,
        DENY
    }

    private final Outcome outcome;
    private final String reason;

    private PolicyDecision(Outcome outcome, String reason) {
        this.outcome = outcome;
        this.reason = reason;
    }

    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(Outcome.ALLOW, reason);
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(Outcome.DENY, reason);
    }

    public boolean isAllowed() {
        return outcome == Outcome.ALLOW;
    }

    public String getReason() {
        return reason;
    }

    public Outcome getOutcome() {
        return outcome;
    }
}
