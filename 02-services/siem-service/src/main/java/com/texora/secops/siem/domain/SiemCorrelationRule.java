package com.texora.secops.siem.domain;

import com.texora.secops.siem.domain.enums.RuleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A detection rule. Only rules with status APPROVED are ever handed to
 * {@code RuleExecutionSandbox}. Every transition of this entity's status is required to be
 * mirrored into {@code siem_rule_change_audit} by {@code RuleApprovalService} - never rely on
 * this entity's own history for the audit trail.
 */
@Entity
@Table(name = "siem_correlation_rule", schema = "siem")
public class SiemCorrelationRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Lob
    @Column(name = "rule_definition", nullable = false)
    private String ruleDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RuleStatus status;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "target_rule_id")
    private UUID targetRuleId;

    @Column(name = "decision_by")
    private UUID decisionBy;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @Column(name = "decision_notes")
    private String decisionNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SiemCorrelationRule() {
        // required by JPA
    }

    public SiemCorrelationRule(UUID id, UUID tenantId, String name, String ruleDefinition,
                                RuleStatus status, UUID submittedBy, UUID targetRuleId,
                                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.ruleDefinition = ruleDefinition;
        this.status = status;
        this.submittedBy = submittedBy;
        this.targetRuleId = targetRuleId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRuleDefinition() {
        return ruleDefinition;
    }

    public void setRuleDefinition(String ruleDefinition) {
        this.ruleDefinition = ruleDefinition;
    }

    public RuleStatus getStatus() {
        return status;
    }

    public void setStatus(RuleStatus status) {
        this.status = status;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public UUID getTargetRuleId() {
        return targetRuleId;
    }

    public void setTargetRuleId(UUID targetRuleId) {
        this.targetRuleId = targetRuleId;
    }

    public UUID getDecisionBy() {
        return decisionBy;
    }

    public void setDecisionBy(UUID decisionBy) {
        this.decisionBy = decisionBy;
    }

    public Instant getDecisionAt() {
        return decisionAt;
    }

    public void setDecisionAt(Instant decisionAt) {
        this.decisionAt = decisionAt;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SiemCorrelationRule)) {
            return false;
        }
        SiemCorrelationRule that = (SiemCorrelationRule) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SiemCorrelationRule{id=" + id + ", name='" + name + "', status=" + status + "}";
    }
}
