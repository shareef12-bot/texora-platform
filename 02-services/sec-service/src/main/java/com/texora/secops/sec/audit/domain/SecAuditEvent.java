package com.texora.secops.sec.audit.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tamper-evident audit event with hash chaining.
 *
 * <p>Each event carries:</p>
 * <ul>
 *   <li>monotonic {@code sequence_no}</li>
 *   <li>{@code event_hash} = hash(canonical content + prev_hash)</li>
 *   <li>{@code prev_hash} from the preceding event</li>
 * </ul>
 *
 * <p>Any modification or deletion breaks the chain at that point and every
 * point after. A scheduled job re-verifies the chain and alerts on any break.</p>
 *
 * <p>The application role has INSERT/SELECT only — no UPDATE or DELETE (B.6 §4).</p>
 */
@Entity
@Table(name = "sec_audit_event", schema = "sec")
public class SecAuditEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // sequence_no is a Postgres BIGSERIAL (see V1__create_sec_schema.sql) —
    // the database generates it on its own via its own sequence. Hibernate
    // never writes to this column (insertable/updatable = false); it only
    // reads back whatever value Postgres assigned. @GeneratedValue is a
    // JPA identifier-generation strategy and is only legal on the entity's
    // actual @Id (which is `id`, the UUID, above) — having it here as well
    // is what breaks entity-manager-factory startup.
    @Column(name = "sequence_no", nullable = false, insertable = false, updatable = false)
    private Long sequenceNo;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 255)
    private String targetId;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Type(JsonBinaryType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    protected SecAuditEvent() {
        // required by JPA
    }

    public SecAuditEvent(UUID id, UUID tenantId, String actorId, String action,
                          String targetType, String targetId, String outcome,
                          Map<String, Object> payload, String prevHash, String eventHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.occurredAt = Instant.now();
        this.payload = payload;
        this.prevHash = prevHash;
        this.eventHash = eventHash;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Long getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Long sequenceNo) { this.sequenceNo = sequenceNo; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }

    public String getEventHash() { return eventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecAuditEvent)) return false;
        SecAuditEvent that = (SecAuditEvent) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "SecAuditEvent{id=" + id + ", seq=" + sequenceNo
                + ", action='" + action + "', outcome='" + outcome + "'}";
    }
}