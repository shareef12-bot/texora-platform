package com.texora.secops.vpn.policy;

public enum PolicyVerdict {
    ALLOW,
    DENY,
    /** Cannot be decided with confidence. Always handled as DENY (establishment) or REVOKE (active session). */
    AMBIGUOUS,
    /** A DENY-effect policy whose conditions do not apply right now. */
    NOT_APPLICABLE
}
