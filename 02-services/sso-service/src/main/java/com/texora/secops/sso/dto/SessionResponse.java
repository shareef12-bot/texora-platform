package com.texora.secops.sso.dto;

import com.texora.secops.sso.domain.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public class SessionResponse {

    private UUID sessionId;
    private UUID dcUserId;
    private UUID applicationId;
    private SessionStatus status;
    private Instant createdAt;
    private Instant expiresAt;

    public SessionResponse() {
    }

    public SessionResponse(UUID sessionId, UUID dcUserId, UUID applicationId, SessionStatus status,
                            Instant createdAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.dcUserId = dcUserId;
        this.applicationId = applicationId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getDcUserId() {
        return dcUserId;
    }

    public void setDcUserId(UUID dcUserId) {
        this.dcUserId = dcUserId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
