package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class GatewayEndpointResponse {

    private UUID id;

    private String name;

    private String endpointUrl;

    private String status;

    private Instant createdAt;

    public GatewayEndpointResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "GatewayEndpointResponse{" + "id=" + id + ", name=" + name + ", endpointUrl=" + endpointUrl + ", status=" + status + ", createdAt=" + createdAt + "}";
    }

}
