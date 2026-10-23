package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AuditEventResponse {

    private UUID id;

    private UUID actorId;

    private String action;

    private String targetType;

    private String targetId;

    private String outcome;

    private Instant occurredAt;

    private Map<String, Object> payload;

    public AuditEventResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "AuditEventResponse{" + "id=" + id + ", actorId=" + actorId + ", action=" + action + ", targetType=" + targetType + ", targetId=" + targetId + ", outcome=" + outcome + ", occurredAt=" + occurredAt + ", payload=" + payload + "}";
    }

}
