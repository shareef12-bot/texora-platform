package com.texora.secops.cgi.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Route allowlist entry (cgi_route_allowlist_entry). Only APPROVED entries are ever matched.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@Entity
@Table(name = "cgi_route_allowlist_entry", schema = "cgi")
public class RouteAllowlistEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "route_pattern", nullable = false, length = 255, updatable = false)
    private String routePattern;

    @Column(name = "approved_handler_id", nullable = false, length = 128, updatable = false)
    private String approvedHandlerId;

    @Column(name = "max_execution_seconds", nullable = false, updatable = false)
    private int maxExecutionSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RouteStatus status;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected RouteAllowlistEntry() {
        // required by JPA
    }

    public RouteAllowlistEntry(UUID id, UUID tenantId, String routePattern, String approvedHandlerId, int maxExecutionSeconds, RouteStatus status, UUID submittedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.routePattern = routePattern;
        this.approvedHandlerId = approvedHandlerId;
        this.maxExecutionSeconds = maxExecutionSeconds;
        this.status = status;
        this.submittedBy = submittedBy;
        this.createdAt = createdAt;
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

    public String getRoutePattern() {
        return routePattern;
    }

    public void setRoutePattern(String routePattern) {
        this.routePattern = routePattern;
    }

    public String getApprovedHandlerId() {
        return approvedHandlerId;
    }

    public void setApprovedHandlerId(String approvedHandlerId) {
        this.approvedHandlerId = approvedHandlerId;
    }

    public int getMaxExecutionSeconds() {
        return maxExecutionSeconds;
    }

    public void setMaxExecutionSeconds(int maxExecutionSeconds) {
        this.maxExecutionSeconds = maxExecutionSeconds;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public void setStatus(RouteStatus status) {
        this.status = status;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteAllowlistEntry)) {
            return false;
        }
        RouteAllowlistEntry that = (RouteAllowlistEntry) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RouteAllowlistEntry{" + "id=" + id + ", routePattern=" + routePattern + ", approvedHandlerId=" + approvedHandlerId + ", status=" + status + "}";
    }

}
