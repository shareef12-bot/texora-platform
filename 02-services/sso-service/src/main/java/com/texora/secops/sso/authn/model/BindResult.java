package com.texora.secops.sso.authn.model;

import java.util.UUID;

/** Outcome of a credential verification call to LDAP's protected bind endpoint. */
public final class BindResult {

    private final boolean success;
    private final UUID dcUserId;
    private final String failureReason;

    private BindResult(boolean success, UUID dcUserId, String failureReason) {
        this.success = success;
        this.dcUserId = dcUserId;
        this.failureReason = failureReason;
    }

    public static BindResult success(UUID dcUserId) {
        return new BindResult(true, dcUserId, null);
    }

    public static BindResult failure(String reason) {
        return new BindResult(false, null, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public UUID getDcUserId() {
        return dcUserId;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
