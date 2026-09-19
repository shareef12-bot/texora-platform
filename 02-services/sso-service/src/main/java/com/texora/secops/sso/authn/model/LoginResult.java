package com.texora.secops.sso.authn.model;

import java.util.UUID;

/** Terminal result of {@code LoginOrchestrationService.login(...)}. */
public final class LoginResult {

    private final LoginOutcome outcome;
    private final UUID sessionId;
    private final UUID dcUserId;

    private LoginResult(LoginOutcome outcome, UUID sessionId, UUID dcUserId) {
        this.outcome = outcome;
        this.sessionId = sessionId;
        this.dcUserId = dcUserId;
    }

    public static LoginResult success(UUID sessionId, UUID dcUserId) {
        return new LoginResult(LoginOutcome.SUCCESS, sessionId, dcUserId);
    }

    public static LoginResult denied(LoginOutcome outcome) {
        if (outcome == LoginOutcome.SUCCESS) {
            throw new IllegalArgumentException("denied() cannot be called with SUCCESS");
        }
        return new LoginResult(outcome, null, null);
    }

    public LoginOutcome getOutcome() {
        return outcome;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getDcUserId() {
        return dcUserId;
    }

    public boolean isSuccess() {
        return outcome == LoginOutcome.SUCCESS;
    }
}
