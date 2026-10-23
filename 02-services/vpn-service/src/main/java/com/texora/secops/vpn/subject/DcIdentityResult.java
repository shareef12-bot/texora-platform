package com.texora.secops.vpn.subject;

public enum DcIdentityResult {
    ACTIVE,
    INACTIVE,
    NOT_FOUND,
    /** Timeout/transport/malformed: identity status cannot be confirmed. */
    UNAVAILABLE
}
