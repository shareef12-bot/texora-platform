package com.texora.secops.cgi.gateway.dto;

import com.texora.secops.cgi.security.CallerContext;

import java.util.Map;

/** One inbound gateway request. Holds RAW parameters/body, so toString prints neither. Immutable by design. */
public final class GatewayCall {

    private final CallerContext caller;
    private final String method;
    private final String routePath;
    private final Map<String, String[]> parameters;
    private final String contentType;
    private final byte[] body;

    public GatewayCall(CallerContext caller, String method, String routePath, Map<String, String[]> parameters,
                       String contentType, byte[] body) {
        this.caller = caller;
        this.method = method;
        this.routePath = routePath;
        this.parameters = parameters == null ? Map.of() : parameters;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body;
    }

    public CallerContext getCaller() {
        return caller;
    }

    public String getMethod() {
        return method;
    }

    public String getRoutePath() {
        return routePath;
    }

    public Map<String, String[]> getParameters() {
        return parameters;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "GatewayCall{tenantId=" + (caller == null ? null : caller.getTenantId()) + ", method=" + method + "}";
    }
}
