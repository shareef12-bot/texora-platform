package com.texora.secops.vpn.security;

/** Result of a Security Server policy decision. Only ALLOW ever lets anything through. */
public enum SecDecision {
    ALLOW,
    DENY,
    /** Timeout, transport error, non-200, malformed body, unknown value - all treated as "cannot confirm". */
    UNAVAILABLE
}
