package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GatewayEndpointRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 512)
    private String endpointUrl;

    public GatewayEndpointRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    @Override
    public String toString() {
        return "GatewayEndpointRequest{" + "name=" + name + ", endpointUrl=" + endpointUrl + "}";
    }

}
