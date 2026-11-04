package com.texora.secops.vpn;

import java.util.Set;
import java.util.UUID;

/** Shared string constants. Persisted as strings so the DB stays readable by incident responders. */
public final class VpnConstants {

    private VpnConstants() {
    }

    /** Actor recorded for control-plane-initiated actions (re-validation, watchdog, dataplane callbacks). */
    public static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    public static final String POLICY_DRAFT = "DRAFT";
    public static final String POLICY_PENDING = "PENDING_APPROVAL";
    public static final String POLICY_APPROVED = "APPROVED";
    public static final String POLICY_DISABLED = "DISABLED";

    public static final String EFFECT_ALLOW = "ALLOW";
    public static final String EFFECT_DENY = "DENY";

    public static final String SESSION_ACTIVE = "ACTIVE";
    public static final String SESSION_REVOKED = "REVOKED";
    public static final String SESSION_EXPIRED = "EXPIRED";

    public static final String SUBJECT_ACTIVE = "ACTIVE";
    public static final String SUBJECT_SUSPENDED = "SUSPENDED";

    public static final String DEVICE_ACTIVE = "ACTIVE";
    public static final String ENDPOINT_ACTIVE = "ACTIVE";

    public static final String TEARDOWN_NONE = "NONE";
    public static final String TEARDOWN_PENDING = "PENDING";
    public static final String TEARDOWN_CONFIRMED = "CONFIRMED";

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_DENIED = "DENIED";
    public static final String OUTCOME_FAILURE = "FAILURE";

    // ---- Revocation triggers ----
    public static final String TRIGGER_ADMIN = "ADMIN";
    public static final String TRIGGER_EXPIRY = "EXPIRY";
    public static final String TRIGGER_DATAPLANE_REPORTED = "DATAPLANE_REPORTED";
    public static final String TRIGGER_ESTABLISH_FAILURE = "ESTABLISH_FAILURE";
    // Uncertainty-triggered (see docs/UNCERTAINTY-TRIGGERS.md)
    public static final String TRIGGER_SEC_UNAVAILABLE = "SEC_UNAVAILABLE";
    public static final String TRIGGER_POLICY_DENY = "POLICY_DENY";
    public static final String TRIGGER_POLICY_AMBIGUOUS = "POLICY_AMBIGUOUS";
    public static final String TRIGGER_POLICY_CHANGED = "POLICY_CHANGED";
    public static final String TRIGGER_IDENTITY_INACTIVE = "IDENTITY_INACTIVE";
    public static final String TRIGGER_IDENTITY_UNCERTAIN = "IDENTITY_UNCERTAIN";
    public static final String TRIGGER_STALE_VALIDATION = "STALE_VALIDATION";
    public static final String TRIGGER_STATE_INCONSISTENT = "STATE_INCONSISTENT";
    public static final String TRIGGER_INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final Set<String> UNCERTAINTY_TRIGGERS = Set.of(
            TRIGGER_SEC_UNAVAILABLE, TRIGGER_POLICY_DENY, TRIGGER_POLICY_AMBIGUOUS, TRIGGER_POLICY_CHANGED,
            TRIGGER_IDENTITY_INACTIVE, TRIGGER_IDENTITY_UNCERTAIN, TRIGGER_STALE_VALIDATION,
            TRIGGER_STATE_INCONSISTENT, TRIGGER_INTERNAL_ERROR);

    /** True for every revocation the control plane itself initiated because it lost confidence (not admin/expiry). */
    public static boolean isUncertaintyTrigger(String trigger) {
        return UNCERTAINTY_TRIGGERS.contains(trigger);
    }

    // ---- Actions (audit) ----
    public static final String ACTION_POLICY_SUBMITTED = "ACCESS_POLICY_SUBMITTED";
    public static final String ACTION_POLICY_APPROVED = "ACCESS_POLICY_APPROVED";
    public static final String ACTION_POLICY_REJECTED = "ACCESS_POLICY_REJECTED";
    public static final String ACTION_POLICY_DISABLED = "ACCESS_POLICY_DISABLED";
    public static final String ACTION_POLICY_ASSIGNED = "ACCESS_POLICY_ASSIGNED";
    public static final String ACTION_SELF_APPROVAL_BLOCKED = "ACCESS_POLICY_SELF_APPROVAL_BLOCKED";
    public static final String ACTION_SESSION_ESTABLISHED = "SESSION_ESTABLISHED";
    public static final String ACTION_SESSION_DENIED = "SESSION_ESTABLISHMENT_DENIED";
    public static final String ACTION_SESSION_REVOKED_ADMIN = "SESSION_REVOKED_ADMIN";
    public static final String ACTION_SESSION_REVOKED_UNCERTAINTY = "SESSION_REVOKED_UNCERTAINTY";
    public static final String ACTION_SESSION_ENDED = "SESSION_ENDED";
    public static final String ACTION_TEARDOWN = "DATAPLANE_TEARDOWN";
    public static final String ACTION_SUBJECT_CREATED = "VPN_SUBJECT_CREATED";
    public static final String ACTION_DEVICE_ENROLLED = "DEVICE_ENROLLED";
    public static final String ACTION_GATEWAY_ENDPOINT_CREATED = "GATEWAY_ENDPOINT_CREATED";
    public static final String ACTION_CONFIG_CHANGED = "CONFIG_CHANGED";
}
