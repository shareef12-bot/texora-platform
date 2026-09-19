package com.texora.secops.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit event record.
 *
 * <p>Schema:
 * <pre>
 * CREATE TABLE audit.audit_event (
 *     id           UUID        NOT NULL PRIMARY KEY,
 *     tenant_id    UUID        NOT NULL,
 *     actor_id     UUID        NOT NULL,
 *     action       VARCHAR(128) NOT NULL,
 *     target_type  VARCHAR(128) NOT NULL,
 *     target_id    VARCHAR(255),
 *     outcome      VARCHAR(32)  NOT NULL,   -- SUCCESS | FAILURE
 *     occurred_at  TIMESTAMPTZ  NOT NULL,
 *     payload      TEXT,
 *     service_name VARCHAR(128) NOT NULL,
 *     kafka_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
 * );
 * -- GRANT SELECT ON audit.audit_event TO app_role;
 * -- No UPDATE or DELETE grant for the application role (standard B.6 §4).
 * </pre>
 *
 * <p>No Lombok. Explicit getters; no setters (immutable after construction).
 */
@Entity
@Table(name = "audit_event", schema = "audit")
public class AuditEvent {

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";

    public static final String KAFKA_STATUS_PENDING   = "PENDING";
    public static final String KAFKA_STATUS_PUBLISHED = "PUBLISHED";
    public static final String KAFKA_STATUS_FAILED    = "FAILED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, updatable = false, length = 128)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 128)
    private String targetType;

    @Column(name = "target_id", updatable = false, length = 255)
    private String targetId;

    @Column(name = "outcome", nullable = false, updatable = false, length = 32)
    private String outcome;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "payload", updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "service_name", nullable = false, updatable = false, length = 128)
    private String serviceName;

    /**
     * Tracks Kafka publishing status. Only this field is mutable post-insert
     * (by the retry mechanism). The application role has UPDATE only on this column.
     */
    @Column(name = "kafka_status", nullable = false, length = 32)
    private String kafkaStatus;

    protected AuditEvent() {
        // required by JPA
    }

    public AuditEvent(UUID id, UUID tenantId, UUID actorId, String action,
                      String targetType, String targetId, String outcome,
                      Instant occurredAt, String payload, String serviceName) {
        this.id          = Objects.requireNonNull(id,          "id");
        this.tenantId    = Objects.requireNonNull(tenantId,    "tenantId");
        this.actorId     = Objects.requireNonNull(actorId,     "actorId");
        this.action      = Objects.requireNonNull(action,      "action");
        this.targetType  = Objects.requireNonNull(targetType,  "targetType");
        this.targetId    = targetId;    // nullable
        this.outcome     = Objects.requireNonNull(outcome,     "outcome");
        this.occurredAt  = Objects.requireNonNull(occurredAt,  "occurredAt");
        this.payload     = payload;     // nullable
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.kafkaStatus = KAFKA_STATUS_PENDING;
    }

    // Getters — no setters except kafkaStatus (mutable for retry)

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPayload() { return payload; }
    public String getServiceName() { return serviceName; }
    public String getKafkaStatus() { return kafkaStatus; }

    public void setKafkaStatus(String kafkaStatus) {
        this.kafkaStatus = Objects.requireNonNull(kafkaStatus, "kafkaStatus");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AuditEvent)) return false;
        AuditEvent that = (AuditEvent) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditEvent{id=" + id + ", action='" + action
            + "', tenantId=" + tenantId + ", outcome='" + outcome + "'}";
    }
}
