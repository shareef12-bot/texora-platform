package com.texora.secops.sso.domain;

/** Lifecycle status of an sso_session row. */
public enum SessionStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
