package com.texora.secops.cgi.timeout.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Execution timeout control as returned over HTTP.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class TimeoutControlResponse {

    private UUID routeAllowlistEntryId;

    private int maxExecutionSeconds;

    private UUID updatedBy;

    private Instant updatedAt;

    public TimeoutControlResponse() {
        // default constructor (JSON / property binding)
    }

    public TimeoutControlResponse(UUID routeAllowlistEntryId, int maxExecutionSeconds, UUID updatedBy, Instant updatedAt) {
        this.routeAllowlistEntryId = routeAllowlistEntryId;
        this.maxExecutionSeconds = maxExecutionSeconds;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
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
        if (!(other instanceof TimeoutControlResponse)) {
            return false;
        }
        TimeoutControlResponse that = (TimeoutControlResponse) other;
        return Objects.equals(routeAllowlistEntryId, that.routeAllowlistEntryId) && Objects.equals(maxExecutionSeconds, that.maxExecutionSeconds) && Objects.equals(updatedBy, that.updatedBy) && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeAllowlistEntryId, maxExecutionSeconds, updatedBy, updatedAt);
    }

    @Override
    public String toString() {
        return "TimeoutControlResponse{" + "routeAllowlistEntryId=" + routeAllowlistEntryId + ", maxExecutionSeconds=" + maxExecutionSeconds + ", updatedBy=" + updatedBy + ", updatedAt=" + updatedAt + "}";
    }

}
