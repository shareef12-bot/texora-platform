package com.texora.secops.cgi.audit.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Immutable request to write one audit event. The payload is redacted by the writer before persistence. */
public final class AuditRecord {

    private final UUID tenantId;
    private final UUID actorId;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final String outcome;
    private final Map<String, Object> payload;

    private AuditRecord(UUID tenantId, UUID actorId, String action, String targetType, String targetId, String outcome,
                        Map<String, Object> payload) {
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.payload = payload == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public static AuditRecord of(UUID tenantId, UUID actorId, String action, String targetType, String targetId,
                                 String outcome, Map<String, Object> payload) {
        return new AuditRecord(tenantId, actorId, action, targetType, targetId, outcome, payload);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getOutcome() {
        return outcome;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "AuditRecord{tenantId=" + tenantId + ", action=" + action + ", outcome=" + outcome + "}";
    }
}
