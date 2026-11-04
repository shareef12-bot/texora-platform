package com.texora.secops.cgi.timeout.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

/**
 * POST /api/v1/execution-timeout-controls request body.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class TimeoutControlRequest {

    @NotNull
    private UUID routeAllowlistEntryId;

    @NotNull
    @Min(1)
    private Integer maxExecutionSeconds;

    public TimeoutControlRequest() {
        // default constructor (JSON / property binding)
    }

    public TimeoutControlRequest(UUID routeAllowlistEntryId, Integer maxExecutionSeconds) {
        this.routeAllowlistEntryId = routeAllowlistEntryId;
        this.maxExecutionSeconds = maxExecutionSeconds;
    }

    public UUID getRouteAllowlistEntryId() {
        return routeAllowlistEntryId;
    }

    public void setRouteAllowlistEntryId(UUID routeAllowlistEntryId) {
        this.routeAllowlistEntryId = routeAllowlistEntryId;
    }

    public Integer getMaxExecutionSeconds() {
        return maxExecutionSeconds;
    }

    public void setMaxExecutionSeconds(Integer maxExecutionSeconds) {
        this.maxExecutionSeconds = maxExecutionSeconds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimeoutControlRequest)) {
            return false;
        }
        TimeoutControlRequest that = (TimeoutControlRequest) other;
        return Objects.equals(routeAllowlistEntryId, that.routeAllowlistEntryId) && Objects.equals(maxExecutionSeconds, that.maxExecutionSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeAllowlistEntryId, maxExecutionSeconds);
    }

    @Override
    public String toString() {
        return "TimeoutControlRequest{" + "routeAllowlistEntryId=" + routeAllowlistEntryId + ", maxExecutionSeconds=" + maxExecutionSeconds + "}";
    }

}
