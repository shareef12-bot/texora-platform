package com.texora.secops.vpn.gateway;

import java.util.UUID;

/**
 * dataplaneSessionRef may be null when establishment outcome was ambiguous; adapters must then revoke by sessionId correlation key.
 */
public class GatewayRevokeRequest {

    private UUID sessionId;

    private String dataplaneSessionRef;

    private String triggerCode;

    public GatewayRevokeRequest() {
    }

    public GatewayRevokeRequest(UUID sessionId, String dataplaneSessionRef, String triggerCode) {
        this.sessionId = sessionId;
        this.dataplaneSessionRef = dataplaneSessionRef;
        this.triggerCode = triggerCode;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getDataplaneSessionRef() {
        return dataplaneSessionRef;
    }

    public void setDataplaneSessionRef(String dataplaneSessionRef) {
        this.dataplaneSessionRef = dataplaneSessionRef;
    }

    public String getTriggerCode() {
        return triggerCode;
    }

    public void setTriggerCode(String triggerCode) {
        this.triggerCode = triggerCode;
    }

    @Override
    public String toString() {
        return "GatewayRevokeRequest{" + "sessionId=" + sessionId + ", dataplaneSessionRef=" + dataplaneSessionRef + ", triggerCode=" + triggerCode + "}";
    }

}
