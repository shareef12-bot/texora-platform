package com.texora.secops.siem.domain;

import com.texora.secops.siem.domain.enums.AlertSeverity;
import com.texora.secops.siem.domain.enums.AlertStatus;
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
 * Created by {@code AlertLifecycleService} when {@code RuleExecutionSandbox} reports a rule
 * match. {@code tenantId} is the column actually used for RBAC/tenant filtering (B.5);
 * {@code tenantScope} is carried separately per the LLD schema and is set equal to the
 * triggering event's tenant at creation time, kept distinct so a future cross-tenant
 * correlation feature cannot silently reuse this column for filtering without a deliberate
 * design decision.
 */
@Entity
@Table(name = "siem_alert", schema = "siem")
public class SiemAlert {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "correlation_rule_id", nullable = false)
    private UUID correlationRuleId;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AlertStatus status;

    @Column(name = "tenant_scope", nullable = false)
    private UUID tenantScope;

    @Column(name = "triggering_event_id", length = 255)
    private String triggeringEventId;

    @Column(name = "summary", length = 1024)
    private String summary;

    protected SiemAlert() {
        // required by JPA
    }

    public SiemAlert(UUID id, UUID tenantId, UUID correlationRuleId, Instant triggeredAt,
                      AlertSeverity severity, AlertStatus status, UUID tenantScope,
                      String triggeringEventId, String summary) {
        this.id = id;
        this.tenantId = tenantId;
        this.correlationRuleId = correlationRuleId;
        this.triggeredAt = triggeredAt;
        this.severity = severity;
        this.status = status;
        this.tenantScope = tenantScope;
        this.triggeringEventId = triggeringEventId;
        this.summary = summary;
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

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public UUID getTenantScope() {
        return tenantScope;
    }

    public void setTenantScope(UUID tenantScope) {
        this.tenantScope = tenantScope;
    }

    public String getTriggeringEventId() {
        return triggeringEventId;
    }

    public void setTriggeringEventId(String triggeringEventId) {
        this.triggeringEventId = triggeringEventId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SiemAlert)) {
            return false;
        }
        SiemAlert that = (SiemAlert) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SiemAlert{id=" + id + ", severity=" + severity + ", status=" + status + "}";
    }
}
