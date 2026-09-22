package com.texora.secops.sso.dto;

import com.texora.secops.sso.domain.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

/** Response body for application-registration endpoints. */
public class ApplicationRegistrationResponse {

    private UUID id;
    private String clientId;
    private String name;
    private String productId;
    private ApplicationStatus status;
    private UUID registeredBy;
    private Instant registeredAt;

    public ApplicationRegistrationResponse() {
    }

    public ApplicationRegistrationResponse(UUID id, String clientId, String name, String productId,
                                            ApplicationStatus status, UUID registeredBy,
                                            Instant registeredAt) {
        this.id = id;
        this.clientId = clientId;
        this.name = name;
        this.productId = productId;
        this.status = status;
        this.registeredBy = registeredBy;
        this.registeredAt = registeredAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public UUID getRegisteredBy() {
        return registeredBy;
    }

    public void setRegisteredBy(UUID registeredBy) {
        this.registeredBy = registeredBy;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }
}
