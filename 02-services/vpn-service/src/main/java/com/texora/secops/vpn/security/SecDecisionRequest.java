package com.texora.secops.vpn.security;

import java.util.UUID;

public class SecDecisionRequest {

    private UUID tenantId;

    private UUID subjectId;

    private String action;

    private String resource;

    public SecDecisionRequest() {
    }

    public SecDecisionRequest(UUID tenantId, UUID subjectId, String action, String resource) {
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.action = action;
        this.resource = resource;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    @Override
    public String toString() {
        return "SecDecisionRequest{" + "tenantId=" + tenantId + ", subjectId=" + subjectId + ", action=" + action + ", resource=" + resource + "}";
    }

}
