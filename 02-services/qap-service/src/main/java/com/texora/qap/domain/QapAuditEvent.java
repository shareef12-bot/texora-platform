package com.texora.qap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only audit event.  The application role must NOT have UPDATE or DELETE
 * on this table (enforced by schema migration).  Each event is also published
 * asynchronously to Kafka topic qap.audit.events.
 */
@Entity
@Table(name = "qap_audit_event", schema = "qap")
public class QapAuditEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", length = 128)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    protected QapAuditEvent() {
        // required by JPA
    }

    public QapAuditEvent(UUID id, UUID tenantId, UUID actorId, String action,
                          String targetType, UUID targetId, String outcome,
                          Instant occurredAt, String payload) {
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QapAuditEvent)) return false;
        QapAuditEvent that = (QapAuditEvent) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "QapAuditEvent{id=" + id + ", action='" + action + "', outcome='" + outcome + "'}";
    }
}
