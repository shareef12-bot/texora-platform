package com.texora.secops.vpn.gateway;

public class GatewayEstablishResult {

    private String dataplaneSessionRef;

    public GatewayEstablishResult() {
    }

    public GatewayEstablishResult(String dataplaneSessionRef) {
        this.dataplaneSessionRef = dataplaneSessionRef;
    }

    public String getDataplaneSessionRef() {
        return dataplaneSessionRef;
    }

    public void setDataplaneSessionRef(String dataplaneSessionRef) {
        this.dataplaneSessionRef = dataplaneSessionRef;
    }

    @Override
    public String toString() {
        return "GatewayEstablishResult{" + "dataplaneSessionRef=" + dataplaneSessionRef + "}";
    }

}
