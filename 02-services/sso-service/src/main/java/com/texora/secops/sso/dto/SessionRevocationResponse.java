package com.texora.secops.sso.dto;

import com.texora.secops.sso.domain.SessionStatus;

import java.time.Instant;
import java.util.UUID;

/** Matches the sample contract in LLD §6.2. */
public class SessionRevocationResponse {

    private UUID sessionId;
    private SessionStatus status;
    private Instant revokedAt;
    private int tokensRevoked;

    public SessionRevocationResponse() {
    }

    public SessionRevocationResponse(UUID sessionId, SessionStatus status, Instant revokedAt,
                                      int tokensRevoked) {
        this.sessionId = sessionId;
        this.status = status;
        this.revokedAt = revokedAt;
        this.tokensRevoked = tokensRevoked;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public int getTokensRevoked() {
        return tokensRevoked;
    }

    public void setTokensRevoked(int tokensRevoked) {
        this.tokensRevoked = tokensRevoked;
    }
}
