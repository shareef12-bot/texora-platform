package com.texora.secops.dc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dc_sync_job", schema = "dc")
public class DcSyncJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", nullable = true, length = 255)
    private String entityId;

    @Column(name = "operation", nullable = false, length = 32)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 512)
    private String idempotencyKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @Column(name = "records_processed", nullable = false)
    private int recordsProcessed;

    @Column(name = "error_summary", nullable = true, length = 1000)
    private String errorSummary;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_retry_at", nullable = true)
    private Instant nextRetryAt;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    public DcSyncJob() {
        // required by JPA / Jackson
    }

    public DcSyncJob(UUID id, UUID tenantId, String source, String entityType, String entityId, String operation, String idempotencyKey, String status, Instant startedAt, Instant completedAt, int recordsProcessed, String errorSummary, int attempts, Instant nextRetryAt, UUID requestedBy, Map<String, Object> payload) {
        this.id = id;
        this.tenantId = tenantId;
        this.source = source;
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.recordsProcessed = recordsProcessed;
        this.errorSummary = errorSummary;
        this.attempts = attempts;
        this.nextRetryAt = nextRetryAt;
        this.requestedBy = requestedBy;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(int recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(UUID requestedBy) {
        this.requestedBy = requestedBy;
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
        DcSyncJob that = (DcSyncJob) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DcSyncJob{id=" + id + ", " + "source=" + source + ", " + "entityType=" + entityType + ", " + "status=" + status + "}";
    }
}
