package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class SessionResponse {

    private UUID sessionId;

    private String status;

    private Instant establishedAt;

    private Instant expiresAt;

    private String gatewayEndpoint;

    public SessionResponse() {
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

    public Instant getEstablishedAt() {
        return establishedAt;
    }

    public void setEstablishedAt(Instant establishedAt) {
        this.establishedAt = establishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getGatewayEndpoint() {
        return gatewayEndpoint;
    }

    public void setGatewayEndpoint(String gatewayEndpoint) {
        this.gatewayEndpoint = gatewayEndpoint;
    }

    @Override
    public String toString() {
        return "SessionResponse{" + "sessionId=" + sessionId + ", status=" + status + ", establishedAt=" + establishedAt + ", expiresAt=" + expiresAt + ", gatewayEndpoint=" + gatewayEndpoint + "}";
    }

}
