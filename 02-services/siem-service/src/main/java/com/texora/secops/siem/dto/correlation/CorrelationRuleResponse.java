package com.texora.secops.siem.dto.correlation;

import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.enums.RuleStatus;

import java.time.Instant;
import java.util.UUID;

public class CorrelationRuleResponse {

    private UUID id;
    private String name;
    private String ruleDefinition;
    private RuleStatus status;
    private UUID submittedBy;
    private UUID targetRuleId;
    private UUID decisionBy;
    private Instant decisionAt;
    private Instant createdAt;
    private Instant updatedAt;

    public CorrelationRuleResponse() {
        // required for JSON serialization
    }

    public static CorrelationRuleResponse from(SiemCorrelationRule rule) {
        CorrelationRuleResponse response = new CorrelationRuleResponse();
        response.setId(rule.getId());
        response.setName(rule.getName());
        response.setRuleDefinition(rule.getRuleDefinition());
        response.setStatus(rule.getStatus());
        response.setSubmittedBy(rule.getSubmittedBy());
        response.setTargetRuleId(rule.getTargetRuleId());
        response.setDecisionBy(rule.getDecisionBy());
        response.setDecisionAt(rule.getDecisionAt());
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
}
