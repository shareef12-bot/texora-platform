package com.texora.secops.vpn.dto;

import java.util.List;

public class GatewayIntegrationStatus {

    private String adapter;

    private boolean stub;

    private boolean dataplaneReachable;

    private List<GatewayEndpointResponse> endpoints;

    public GatewayIntegrationStatus() {
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public boolean isStub() {
        return stub;
    }

    public void setStub(boolean stub) {
        this.stub = stub;
    }

    public boolean isDataplaneReachable() {
        return dataplaneReachable;
    }

    public void setDataplaneReachable(boolean dataplaneReachable) {
        this.dataplaneReachable = dataplaneReachable;
    }

    public List<GatewayEndpointResponse> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<GatewayEndpointResponse> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public String toString() {
        return "GatewayIntegrationStatus{" + "adapter=" + adapter + ", stub=" + stub + ", dataplaneReachable=" + dataplaneReachable + ", endpoints=" + endpoints + "}";
    }

}
