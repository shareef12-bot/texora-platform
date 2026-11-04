package com.texora.secops.vpn.security;

import java.util.UUID;

public class Caller {

    private UUID tenantId;

    private UUID userId;

    public Caller() {
    }

    public Caller(UUID tenantId, UUID userId) {
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Caller{" + "tenantId=" + tenantId + ", userId=" + userId + "}";
    }

}
