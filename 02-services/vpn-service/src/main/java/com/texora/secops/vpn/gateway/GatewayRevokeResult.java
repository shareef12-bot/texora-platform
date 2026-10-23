package com.texora.secops.vpn.gateway;

public class GatewayRevokeResult {

    private boolean confirmed;

    public GatewayRevokeResult() {
    }

    public GatewayRevokeResult(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    @Override
    public String toString() {
        return "GatewayRevokeResult{" + "confirmed=" + confirmed + "}";
    }

}
