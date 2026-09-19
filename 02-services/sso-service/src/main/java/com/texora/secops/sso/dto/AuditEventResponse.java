package com.texora.secops.sso.dto;

import com.texora.secops.sso.domain.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

public class AuditEventResponse {

    private UUID id;
    private UUID actorId;
    private String action;
    private String targetType;
    private String targetId;
    private AuditOutcome outcome;
    private Instant occurredAt;

    public AuditEventResponse() {
    }

    public AuditEventResponse(UUID id, UUID actorId, String action, String targetType, String targetId,
                               AuditOutcome outcome, Instant occurredAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
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

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AuditOutcome outcome) {
        this.outcome = outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
