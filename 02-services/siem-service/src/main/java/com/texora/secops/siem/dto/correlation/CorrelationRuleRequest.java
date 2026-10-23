package com.texora.secops.siem.dto.correlation;

import java.util.UUID;

/**
 * Submits either a brand-new rule (leave {@code targetRuleId} null) or a request to disable an
 * existing APPROVED rule (set {@code targetRuleId}, {@code name}/{@code ruleDefinition} ignored).
 */
public class CorrelationRuleRequest {

    private String name;
    private String ruleDefinition;
    private UUID targetRuleId;

    public CorrelationRuleRequest() {
        // required for JSON deserialization
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

    public UUID getTargetRuleId() {
        return targetRuleId;
    }

    public void setTargetRuleId(UUID targetRuleId) {
        this.targetRuleId = targetRuleId;
    }
}
