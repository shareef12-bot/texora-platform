package com.texora.secops.cgi.security;

/** Result of a Security Server policy evaluation. Anything but an explicit ALLOW is a DENY. */
public final class PolicyDecision {

    public static final String REASON_ALLOWED = "ALLOWED";
    public static final String REASON_SEC_DENY = "SEC_DENY";
    public static final String REASON_SEC_TIMEOUT = "SEC_TIMEOUT";
    public static final String REASON_SEC_UNREACHABLE = "SEC_UNREACHABLE";
    public static final String REASON_SEC_MALFORMED = "SEC_MALFORMED_RESPONSE";
    public static final String REASON_SEC_ERROR = "SEC_ERROR";

    private final boolean allowed;
    private final String reasonCode;

    private PolicyDecision(boolean allowed, String reasonCode) {
        this.allowed = allowed;
        this.reasonCode = reasonCode;
    }

    public static PolicyDecision allow() {
        return new PolicyDecision(true, REASON_ALLOWED);
    }

    public static PolicyDecision deny(String reasonCode) {
        return new PolicyDecision(false, reasonCode);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public boolean isTimeout() {
        return REASON_SEC_TIMEOUT.equals(reasonCode);
    }

    @Override
    public String toString() {
        return "PolicyDecision{allowed=" + allowed + ", reasonCode=" + reasonCode + "}";
    }
}
