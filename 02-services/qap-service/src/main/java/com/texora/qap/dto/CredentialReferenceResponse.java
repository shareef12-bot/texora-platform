package com.texora.qap.dto;

import java.time.Instant;
import java.util.UUID;

public class CredentialReferenceResponse {

    private UUID id;
    private UUID tenantId;
    private String productId;
    private String description;
    private Instant createdAt;

    public CredentialReferenceResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
