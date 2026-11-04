package com.texora.secops.vpn.gateway;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately contains NO credential material. The real adapter fetches what it needs from SEC/KMS at point of use.
 */
public class GatewayEstablishRequest {

    private UUID sessionId;

    private UUID tenantId;

    private UUID subjectId;

    private String gatewayEndpoint;

    private Instant expiresAt;

    public GatewayEstablishRequest() {
    }

    public GatewayEstablishRequest(UUID sessionId, UUID tenantId, UUID subjectId, String gatewayEndpoint, Instant expiresAt) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.gatewayEndpoint = gatewayEndpoint;
        this.expiresAt = expiresAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getGatewayEndpoint() {
        return gatewayEndpoint;
    }

    public void setGatewayEndpoint(String gatewayEndpoint) {
        this.gatewayEndpoint = gatewayEndpoint;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "GatewayEstablishRequest{" + "sessionId=" + sessionId + ", tenantId=" + tenantId + ", subjectId=" + subjectId + ", gatewayEndpoint=" + gatewayEndpoint + ", expiresAt=" + expiresAt + "}";
    }

}
