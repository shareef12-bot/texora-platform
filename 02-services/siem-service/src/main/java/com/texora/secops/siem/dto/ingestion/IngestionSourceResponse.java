package com.texora.secops.siem.dto.ingestion;

import com.texora.secops.siem.domain.SiemIngestionSource;
import com.texora.secops.siem.domain.enums.Protocol;
import com.texora.secops.siem.domain.enums.SourceStatus;

import java.time.Instant;
import java.util.UUID;

public class IngestionSourceResponse {

    private UUID id;
    private String name;
    private Protocol protocol;
    private SourceStatus status;
    private String productId;
    private Instant registeredAt;

    public IngestionSourceResponse() {
        // required for JSON serialization
    }

    public static IngestionSourceResponse from(SiemIngestionSource source) {
        IngestionSourceResponse response = new IngestionSourceResponse();
        response.setId(source.getId());
        response.setName(source.getName());
        response.setProtocol(source.getProtocol());
        response.setStatus(source.getStatus());
        response.setProductId(source.getProductId());
        response.setRegisteredAt(source.getRegisteredAt());
        return response;
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

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public void setStatus(SourceStatus status) {
        this.status = status;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }
}
