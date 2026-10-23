package com.texora.secops.vpn.session;

import java.util.UUID;

public class RevocationCommand {

    private UUID tenantId;

    private UUID sessionId;

    private String trigger;

    private String reason;

    private UUID actorId;

    private String terminalStatus;

    private boolean callDataplane;

    private boolean dataplaneMayExist;

    private String knownDataplaneRef;

    public RevocationCommand() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getTerminalStatus() {
        return terminalStatus;
    }

    public void setTerminalStatus(String terminalStatus) {
        this.terminalStatus = terminalStatus;
    }

    public boolean isCallDataplane() {
        return callDataplane;
    }

    public void setCallDataplane(boolean callDataplane) {
        this.callDataplane = callDataplane;
    }

    public boolean isDataplaneMayExist() {
        return dataplaneMayExist;
    }

    public void setDataplaneMayExist(boolean dataplaneMayExist) {
        this.dataplaneMayExist = dataplaneMayExist;
    }

    public String getKnownDataplaneRef() {
        return knownDataplaneRef;
    }

    public void setKnownDataplaneRef(String knownDataplaneRef) {
        this.knownDataplaneRef = knownDataplaneRef;
    }

    @Override
    public String toString() {
        return "RevocationCommand{" + "tenantId=" + tenantId + ", sessionId=" + sessionId + ", trigger=" + trigger + ", reason=" + reason + ", actorId=" + actorId + ", terminalStatus=" + terminalStatus + ", callDataplane=" + callDataplane + ", dataplaneMayExist=" + dataplaneMayExist + ", knownDataplaneRef=" + knownDataplaneRef + "}";
    }

}
