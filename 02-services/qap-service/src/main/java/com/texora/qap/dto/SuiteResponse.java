package com.texora.qap.dto;

import java.time.Instant;
import java.util.UUID;

public class SuiteResponse {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String targetSystem;
    private String suiteType;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public SuiteResponse() {}

    public SuiteResponse(UUID id, UUID tenantId, String name, String targetSystem,
                         String suiteType, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.targetSystem = targetSystem;
        this.suiteType = suiteType;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }

    public String getSuiteType() { return suiteType; }
    public void setSuiteType(String suiteType) { this.suiteType = suiteType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
