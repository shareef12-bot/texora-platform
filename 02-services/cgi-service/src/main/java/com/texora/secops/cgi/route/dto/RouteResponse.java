package com.texora.secops.cgi.route.dto;

import com.texora.secops.cgi.route.domain.RouteStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Route allowlist entry as returned over HTTP (entities are never exposed).
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class RouteResponse {

    private UUID id;

    private RouteStatus status;

    private String routePattern;

    private String approvedHandlerId;

    private int maxExecutionSeconds;

    private UUID submittedBy;

    private Instant createdAt;

    public RouteResponse() {
        // default constructor (JSON / property binding)
    }

    public RouteResponse(UUID id, RouteStatus status, String routePattern, String approvedHandlerId, int maxExecutionSeconds, UUID submittedBy, Instant createdAt) {
        this.id = id;
        this.status = status;
        this.routePattern = routePattern;
        this.approvedHandlerId = approvedHandlerId;
        this.maxExecutionSeconds = maxExecutionSeconds;
        this.submittedBy = submittedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public void setStatus(RouteStatus status) {
        this.status = status;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteResponse)) {
            return false;
        }
        RouteResponse that = (RouteResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(status, that.status) && Objects.equals(routePattern, that.routePattern) && Objects.equals(approvedHandlerId, that.approvedHandlerId) && Objects.equals(maxExecutionSeconds, that.maxExecutionSeconds) && Objects.equals(submittedBy, that.submittedBy) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, routePattern, approvedHandlerId, maxExecutionSeconds, submittedBy, createdAt);
    }

    @Override
    public String toString() {
        return "RouteResponse{" + "id=" + id + ", status=" + status + ", routePattern=" + routePattern + ", approvedHandlerId=" + approvedHandlerId + ", maxExecutionSeconds=" + maxExecutionSeconds + ", submittedBy=" + submittedBy + ", createdAt=" + createdAt + "}";
    }

}
