package com.texora.secops.cgi.execution.adapter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a worker needs. Contains RAW (unredacted) parameters and body because the handler needs them, so
 * {@link #toString()} deliberately prints none of it. The handler id comes from the approved route, never from
 * the request. No Authorization header or token is ever passed to a handler.
 */
public final class WorkerSpawnRequest {

    private final UUID tenantId;
    private final UUID callerSubject;
    private final UUID correlationId;
    private final IsolationSpec isolation;
    private final String method;
    private final String routePath;
    private final String contentType;
    private final Map<String, List<String>> parameters;
    private final byte[] body;

    public WorkerSpawnRequest(UUID tenantId, UUID callerSubject, UUID correlationId, IsolationSpec isolation, String method,
                              String routePath, String contentType, Map<String, List<String>> parameters, byte[] body) {
        this.tenantId = tenantId;
        this.callerSubject = callerSubject;
        this.correlationId = correlationId;
        this.isolation = isolation;
        this.method = method;
        this.routePath = routePath;
        this.contentType = contentType;
        this.parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.body = body == null ? new byte[0] : body.clone();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCallerSubject() {
        return callerSubject;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public IsolationSpec getIsolation() {
        return isolation;
    }

    public String getHandlerId() {
        return isolation == null ? null : isolation.getHandlerId();
    }

    public String getMethod() {
        return method;
    }

    public String getRoutePath() {
        return routePath;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, List<String>> getParameters() {
        return parameters;
    }

    public byte[] getBody() {
        return body.clone();
    }

    @Override
    public String toString() {
        return "WorkerSpawnRequest{handlerId=" + getHandlerId() + ", correlationId=" + correlationId + "}";
    }
}
