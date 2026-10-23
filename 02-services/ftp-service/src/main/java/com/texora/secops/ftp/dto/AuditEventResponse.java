package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class AuditEventResponse {

    private UUID id;

    private String actorId;

    private String action;

    private String targetType;

    private UUID targetId;

    private String outcome;

    private Instant occurredAt;

    private String payload;

    public AuditEventResponse() {
    }

    public AuditEventResponse(UUID id, String actorId, String action, String targetType, UUID targetId, String outcome, Instant occurredAt, String payload) {
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

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
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

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
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

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuditEventResponse)) {
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
        return "AuditEventResponse{" + "id=" + id + ", actorId=" + actorId + ", action=" + action + ", targetType=" + targetType + ", targetId=" + targetId + ", outcome=" + outcome + ", occurredAt=" + occurredAt + ", payload=" + payload + "}";
    }
}
