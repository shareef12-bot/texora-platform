package com.texora.secops.sso.authn.model;

/** High-level result of a full login orchestration run, used to drive the audit event. */
public enum LoginOutcome {
    SUCCESS,
    APPLICATION_UNKNOWN,
    BIND_FAILED,
    MFA_FAILED,
    MFA_NOT_ENROLLED,
    POLICY_DENIED,
    CONSENT_MISSING
}
