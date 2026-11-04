package com.texora.secops.siem.security;

import java.util.UUID;

/** Request body sent to SEC's policy evaluation endpoint. Internal to PolicyDecisionClient. */
public class EvaluatePolicyRequest {

    private UUID tenantId;
    private String action;
    private String resourceScope;

    public EvaluatePolicyRequest() {
        // required for JSON (de)serialization
    }

    public EvaluatePolicyRequest(UUID tenantId, String action, String resourceScope) {
        this.tenantId = tenantId;
        this.action = action;
        this.resourceScope = resourceScope;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceScope() {
        return resourceScope;
    }

    public void setResourceScope(String resourceScope) {
        this.resourceScope = resourceScope;
    }
}
