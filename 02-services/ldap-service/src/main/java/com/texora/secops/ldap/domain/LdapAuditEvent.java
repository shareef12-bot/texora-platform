package com.texora.secops.ldap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit event record for the LDAP service.
 * Lives in {@code ldap.ldap_audit_event}.
 *
 * <p>Never updated or deleted by the application — the application role
 * holds no UPDATE/DELETE grant on this table (per security requirement B.6.4).
 *
 * <p>Also published to Kafka topic {@code ldap.audit.events} for SIEM consumption.
 */
@Entity
@Table(name = "ldap_audit_event", schema = "ldap")
public class LdapAuditEvent {

    public enum Outcome {
        SUCCESS, FAILURE, DENIED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", length = 256)
    private String targetId;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    protected LdapAuditEvent() {
        // required by JPA
    }

    public LdapAuditEvent(UUID id, UUID tenantId, UUID actorId, String action,
                           String targetType, String targetId, Outcome outcome,
                           Instant occurredAt, Map<String, Object> payload) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome.name();
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

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Outcome getOutcomeEnum() {
        return Outcome.valueOf(this.outcome);
    }

    public void setOutcomeEnum(Outcome outcome) {
        this.outcome = outcome.name();
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
        if (!(other instanceof LdapAuditEvent)) {
            return false;
        }
        LdapAuditEvent that = (LdapAuditEvent) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LdapAuditEvent{id=" + id + ", action='" + action + "', outcome='" + outcome + "'}";
    }
}
