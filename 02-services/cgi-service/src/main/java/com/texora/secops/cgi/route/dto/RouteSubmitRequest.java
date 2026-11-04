package com.texora.secops.cgi.route.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * POST /api/v1/route-allowlist request body.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class RouteSubmitRequest {

    @NotBlank
    @Size(max = 255)
    private String routePattern;

    @NotBlank
    @Size(max = 128)
    private String approvedHandlerId;

    @NotNull
    @Min(1)
    private Integer maxExecutionSeconds;

    public RouteSubmitRequest() {
        // default constructor (JSON / property binding)
    }

    public RouteSubmitRequest(String routePattern, String approvedHandlerId, Integer maxExecutionSeconds) {
        this.routePattern = routePattern;
        this.approvedHandlerId = approvedHandlerId;
        this.maxExecutionSeconds = maxExecutionSeconds;
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
        if (!(other instanceof RouteSubmitRequest)) {
            return false;
        }
        RouteSubmitRequest that = (RouteSubmitRequest) other;
        return Objects.equals(routePattern, that.routePattern) && Objects.equals(approvedHandlerId, that.approvedHandlerId) && Objects.equals(maxExecutionSeconds, that.maxExecutionSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routePattern, approvedHandlerId, maxExecutionSeconds);
    }

    @Override
    public String toString() {
        return "RouteSubmitRequest{" + "routePattern=" + routePattern + ", approvedHandlerId=" + approvedHandlerId + ", maxExecutionSeconds=" + maxExecutionSeconds + "}";
    }

}
