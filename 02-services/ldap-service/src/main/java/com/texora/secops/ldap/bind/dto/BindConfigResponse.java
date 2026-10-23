package com.texora.secops.ldap.bind.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a bind configuration record. No credential material is included. */
public class BindConfigResponse {

    private UUID id;
    private UUID tenantId;
    private String bindDn;
    private boolean tlsRequired;
    private String allowedSource;
    private String status;
    private UUID createdBy;
    private Instant createdAt;
    private UUID updatedBy;
    private Instant updatedAt;

    public BindConfigResponse() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getBindDn() { return bindDn; }
    public void setBindDn(String bindDn) { this.bindDn = bindDn; }
    public boolean isTlsRequired() { return tlsRequired; }
    public void setTlsRequired(boolean tlsRequired) { this.tlsRequired = tlsRequired; }
    public String getAllowedSource() { return allowedSource; }
    public void setAllowedSource(String allowedSource) { this.allowedSource = allowedSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
