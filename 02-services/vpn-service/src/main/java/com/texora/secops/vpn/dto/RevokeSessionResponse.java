package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class RevokeSessionResponse {

    private UUID sessionId;

    private String status;

    private Instant revokedAt;

    private boolean dataplaneRevocationConfirmed;

    public RevokeSessionResponse() {
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isDataplaneRevocationConfirmed() {
        return dataplaneRevocationConfirmed;
    }

    public void setDataplaneRevocationConfirmed(boolean dataplaneRevocationConfirmed) {
        this.dataplaneRevocationConfirmed = dataplaneRevocationConfirmed;
    }

    @Override
    public String toString() {
        return "RevokeSessionResponse{" + "sessionId=" + sessionId + ", status=" + status + ", revokedAt=" + revokedAt + ", dataplaneRevocationConfirmed=" + dataplaneRevocationConfirmed + "}";
    }

}
