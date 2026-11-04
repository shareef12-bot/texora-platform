package com.texora.secops.vpn.policy;

public final class PolicyEvaluation {

    private final PolicyVerdict verdict;
    private final String reasonCode;

    public PolicyEvaluation(PolicyVerdict verdict, String reasonCode) {
        this.verdict = verdict;
        this.reasonCode = reasonCode;
    }

    public PolicyVerdict getVerdict() {
        return verdict;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    @Override
    public String toString() {
        return "PolicyEvaluation{verdict=" + verdict + ", reasonCode='" + reasonCode + "'}";
    }
}
