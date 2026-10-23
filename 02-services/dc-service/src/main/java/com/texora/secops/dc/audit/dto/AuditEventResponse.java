package com.texora.secops.dc.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
        // required by Jackson
    }

    public AuditEventResponse(UUID id, UUID actorId, String action, String targetType, String targetId, String outcome, Instant occurredAt, Map<String, Object> payload) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
        this.payload = payload;
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        AuditEventResponse that = (AuditEventResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(actorId, that.actorId) && Objects.equals(action, that.action) && Objects.equals(targetType, that.targetType) && Objects.equals(targetId, that.targetId) && Objects.equals(outcome, that.outcome) && Objects.equals(occurredAt, that.occurredAt) && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, actorId, action, targetType, targetId, outcome, occurredAt, payload);
    }

    @Override
    public String toString() {
        return "AuditEventResponse{id=" + id + ", " + "action=" + action + ", " + "outcome=" + outcome + "}";
    }
}
