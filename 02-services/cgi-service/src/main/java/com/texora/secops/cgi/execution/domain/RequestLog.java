package com.texora.secops.cgi.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Gateway request log (cgi_request_log). Holds REDACTED parameters only.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@Entity
@Table(name = "cgi_request_log", schema = "cgi")
public class RequestLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "route_allowlist_entry_id", nullable = false, updatable = false)
    private UUID routeAllowlistEntryId;

    @Column(name = "caller_sso_subject", nullable = false, updatable = false)
    private UUID callerSsoSubject;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redacted_params", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> redactedParams;

    protected RequestLog() {
        // required by JPA
    }

    public RequestLog(UUID id, UUID tenantId, UUID routeAllowlistEntryId, UUID callerSsoSubject, Instant receivedAt, Map<String, Object> redactedParams) {
        this.id = id;
        this.tenantId = tenantId;
        this.routeAllowlistEntryId = routeAllowlistEntryId;
        this.callerSsoSubject = callerSsoSubject;
        this.receivedAt = receivedAt;
        this.redactedParams = redactedParams;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRouteAllowlistEntryId() {
        return routeAllowlistEntryId;
    }

    public void setRouteAllowlistEntryId(UUID routeAllowlistEntryId) {
        this.routeAllowlistEntryId = routeAllowlistEntryId;
    }

    public UUID getCallerSsoSubject() {
        return callerSsoSubject;
    }

    public void setCallerSsoSubject(UUID callerSsoSubject) {
        this.callerSsoSubject = callerSsoSubject;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Map<String, Object> getRedactedParams() {
        return redactedParams;
    }

    public void setRedactedParams(Map<String, Object> redactedParams) {
        this.redactedParams = redactedParams;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RequestLog)) {
            return false;
        }
        RequestLog that = (RequestLog) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RequestLog{" + "id=" + id + ", routeAllowlistEntryId=" + routeAllowlistEntryId + ", receivedAt=" + receivedAt + "}";
    }

}
