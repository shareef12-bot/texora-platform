package com.texora.secops.vpn.subject;

public enum IdentityState {
    ACTIVE,
    INACTIVE,
    /** Identity status is older than the staleness threshold and could not be refreshed from DC. */
    UNCERTAIN
}
