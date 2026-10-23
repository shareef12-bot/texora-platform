package com.texora.secops.dc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dc_audit_publication", schema = "dc")
public class DcAuditPublication {

    @Id
    @Column(name = "audit_event_id", nullable = false, updatable = false)
    private UUID auditEventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;

    @Column(name = "last_error", nullable = true, length = 500)
    private String lastError;

    public DcAuditPublication() {
        // required by JPA / Jackson
    }

    public DcAuditPublication(UUID auditEventId, UUID tenantId, int attempts, Instant nextAttemptAt, Instant publishedAt, String lastError) {
        this.auditEventId = auditEventId;
        this.tenantId = tenantId;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.publishedAt = publishedAt;
        this.lastError = lastError;
    }

    public UUID getAuditEventId() {
        return auditEventId;
    }

    public void setAuditEventId(UUID auditEventId) {
        this.auditEventId = auditEventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DcAuditPublication that = (DcAuditPublication) other;
        return Objects.equals(auditEventId, that.auditEventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auditEventId);
    }

    @Override
    public String toString() {
        return "DcAuditPublication{auditEventId=" + auditEventId + ", " + "attempts=" + attempts + ", " + "publishedAt=" + publishedAt + "}";
    }
}
