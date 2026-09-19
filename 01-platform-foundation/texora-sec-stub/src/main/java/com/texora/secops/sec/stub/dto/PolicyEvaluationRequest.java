package com.texora.secops.sec.stub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/policies/evaluate}.
 *
 * <p>Contract matches SEC LLD §6.2. All six dependent services are built
 * against this schema — do not change field names without a version bump.
 *
 * <pre>{@code
 * POST /api/v1/policies/evaluate
 * {
 *   "tenantId":    "uuid",
 *   "subjectId":   "uuid",        // caller (user or service account)
 *   "resourceType": "VPN_GATEWAY", // e.g. VPN_GATEWAY, LDAP_USER, AUDIT_LOG
 *   "resourceId":  "uuid",        // nullable for collection-level checks
 *   "action":      "CONNECT",     // verb: READ, WRITE, CONNECT, APPROVE, etc.
 *   "context":     { ... }        // optional free-form context (IP, time, etc.)
 * }
 * }</pre>
 */
public class PolicyEvaluationRequest {

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("subjectId")
    private UUID subjectId;

    @JsonProperty("resourceType")
    private String resourceType;

    @JsonProperty("resourceId")
    private UUID resourceId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("context")
    private Map<String, Object> context;

    public PolicyEvaluationRequest() {
        // Jackson
    }

    public PolicyEvaluationRequest(UUID tenantId, UUID subjectId, String resourceType,
                                    UUID resourceId, String action,
                                    Map<String, Object> context) {
        this.tenantId     = tenantId;
        this.subjectId    = subjectId;
        this.resourceType = resourceType;
        this.resourceId   = resourceId;
        this.action       = action;
        this.context      = context;
    }

    public UUID getTenantId()        { return tenantId; }
    public void setTenantId(UUID v)  { this.tenantId = v; }

    public UUID getSubjectId()         { return subjectId; }
    public void setSubjectId(UUID v)   { this.subjectId = v; }

    public String getResourceType()          { return resourceType; }
    public void setResourceType(String v)    { this.resourceType = v; }

    public UUID getResourceId()        { return resourceId; }
    public void setResourceId(UUID v)  { this.resourceId = v; }

    public String getAction()          { return action; }
    public void setAction(String v)    { this.action = v; }

    public Map<String, Object> getContext()       { return context; }
    public void setContext(Map<String, Object> v) { this.context = v; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PolicyEvaluationRequest)) return false;
        PolicyEvaluationRequest that = (PolicyEvaluationRequest) other;
        return Objects.equals(tenantId, that.tenantId)
            && Objects.equals(subjectId, that.subjectId)
            && Objects.equals(resourceType, that.resourceType)
            && Objects.equals(resourceId, that.resourceId)
            && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, subjectId, resourceType, resourceId, action);
    }

    @Override
    public String toString() {
        return "PolicyEvaluationRequest{tenantId=" + tenantId
            + ", subjectId=" + subjectId
            + ", resourceType='" + resourceType + "'"
            + ", action='" + action + "'}";
    }
}
