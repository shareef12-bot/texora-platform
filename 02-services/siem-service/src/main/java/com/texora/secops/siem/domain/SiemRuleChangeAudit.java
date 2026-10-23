package com.texora.secops.siem.domain;

import com.texora.secops.siem.domain.enums.RuleChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only. Every correlation-rule change - created, approved, rejected, or disabled - is
 * written here regardless of outcome (Requirement 4). The application DB role has no
 * UPDATE/DELETE grant on this table (see V1 migration).
 */
@Entity
@Table(name = "siem_rule_change_audit", schema = "siem")
public class SiemRuleChangeAudit {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "correlation_rule_id", nullable = false)
    private UUID correlationRuleId;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private RuleChangeType changeType;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "notes")
    private String notes;

    protected SiemRuleChangeAudit() {
        // required by JPA
    }

    public SiemRuleChangeAudit(UUID id, UUID tenantId, UUID correlationRuleId, UUID changedBy,
                                RuleChangeType changeType, Instant changedAt, String notes) {
        this.id = id;
        this.tenantId = tenantId;
        this.correlationRuleId = correlationRuleId;
        this.changedBy = changedBy;
        this.changeType = changeType;
        this.changedAt = changedAt;
        this.notes = notes;
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

    public UUID getCorrelationRuleId() {
        return correlationRuleId;
    }

    public void setCorrelationRuleId(UUID correlationRuleId) {
        this.correlationRuleId = correlationRuleId;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(UUID changedBy) {
        this.changedBy = changedBy;
    }

    public RuleChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(RuleChangeType changeType) {
        this.changeType = changeType;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SiemRuleChangeAudit)) {
            return false;
        }
        SiemRuleChangeAudit that = (SiemRuleChangeAudit) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SiemRuleChangeAudit{id=" + id + ", changeType=" + changeType + "}";
    }
}
