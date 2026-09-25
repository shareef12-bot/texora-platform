package com.texora.secops.sso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
/**
 * Append-only audit record. Never updated or deleted by the application —
 * the application DB role is granted no UPDATE/DELETE on this table.
 * Also published to Kafka topic sso.audit.events for SIEM.
 */
@Entity
@Table(name = "sso_audit_event", schema = "sso")
public class SsoAuditEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private AuditOutcome outcome;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    protected SsoAuditEvent() {
        // required by JPA
    }

    public SsoAuditEvent(UUID id, UUID tenantId, UUID actorId, String action, String targetType,
                          String targetId, AuditOutcome outcome, Instant occurredAt, String payload) {
        this.id = id;
        this.tenantId = tenantId;
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

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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
        if (!(other instanceof SsoAuditEvent)) {
            return false;
        }
        SsoAuditEvent that = (SsoAuditEvent) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SsoAuditEvent{id=" + id + ", action='" + action + "', outcome=" + outcome + "}";
    }
}
