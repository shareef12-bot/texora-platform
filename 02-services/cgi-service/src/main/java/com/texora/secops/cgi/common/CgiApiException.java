package com.texora.secops.cgi.common;

import org.springframework.http.HttpStatus;

/**
 * The single service-specific API exception. Messages are fixed strings: they must never carry request
 * parameters, tokens or any other sensitive value.
 */
public class CgiApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    public CgiApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static CgiApiException unauthenticated() {
        return new CgiApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }

    public static CgiApiException rbacDenied() {
        return new CgiApiException(HttpStatus.FORBIDDEN, "RBAC_DENIED", "Caller lacks required role");
    }

    public static CgiApiException rbacNotConfigured() {
        return new CgiApiException(HttpStatus.FORBIDDEN, "RBAC_NOT_CONFIGURED", "Endpoint has no access rule; denied");
    }

    public static CgiApiException crossTenant() {
        return new CgiApiException(HttpStatus.FORBIDDEN, "CROSS_TENANT_DENIED", "Resource belongs to another tenant");
    }

    public static CgiApiException selfApproval() {
        return new CgiApiException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_BLOCKED", "Submitter cannot approve their own change");
    }

    public static CgiApiException policyDenied() {
        return new CgiApiException(HttpStatus.FORBIDDEN, "POLICY_DENIED", "Request denied by policy");
    }

    public static CgiApiException policyTimeout() {
        return new CgiApiException(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "Policy decision timed out; request denied");
    }

    public static CgiApiException notFound(String what) {
        return new CgiApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static CgiApiException routeNotAllowlisted() {
        return new CgiApiException(HttpStatus.NOT_FOUND, "ROUTE_NOT_ALLOWLISTED", "No approved route matches this request");
    }

    public static CgiApiException conflict(String message) {
        return new CgiApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static CgiApiException unprocessable(String code, String message) {
        return new CgiApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static CgiApiException executionTimeout() {
        return new CgiApiException(HttpStatus.GATEWAY_TIMEOUT, "EXECUTION_TIMEOUT", "Handler exceeded its time limit and was terminated");
    }

    public static CgiApiException executionFailed() {
        return new CgiApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXECUTION_FAILED", "Handler execution failed");
    }

    public static CgiApiException gatewayBusy() {
        return new CgiApiException(HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_BUSY", "Gateway is at capacity; try again later");
    }

    public static CgiApiException internal(String code, String message) {
        return new CgiApiException(HttpStatus.INTERNAL_SERVER_ERROR, code, message);
    }
}
