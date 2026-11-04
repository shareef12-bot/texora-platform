package com.texora.secops.cgi.timeout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-route execution timeout override (cgi_execution_timeout_control).
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@Entity
@Table(name = "cgi_execution_timeout_control", schema = "cgi")
public class ExecutionTimeoutControl {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "route_allowlist_entry_id", nullable = false, updatable = false)
    private UUID routeAllowlistEntryId;

    @Column(name = "max_execution_seconds", nullable = false)
    private int maxExecutionSeconds;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExecutionTimeoutControl() {
        // required by JPA
    }

    public ExecutionTimeoutControl(UUID id, UUID tenantId, UUID routeAllowlistEntryId, int maxExecutionSeconds, UUID updatedBy, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.routeAllowlistEntryId = routeAllowlistEntryId;
        this.maxExecutionSeconds = maxExecutionSeconds;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
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

    public int getMaxExecutionSeconds() {
        return maxExecutionSeconds;
    }

    public void setMaxExecutionSeconds(int maxExecutionSeconds) {
        this.maxExecutionSeconds = maxExecutionSeconds;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExecutionTimeoutControl)) {
            return false;
        }
        ExecutionTimeoutControl that = (ExecutionTimeoutControl) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ExecutionTimeoutControl{" + "id=" + id + ", tenantId=" + tenantId + ", routeAllowlistEntryId=" + routeAllowlistEntryId + ", maxExecutionSeconds=" + maxExecutionSeconds + ", updatedBy=" + updatedBy + ", updatedAt=" + updatedAt + "}";
    }

}
