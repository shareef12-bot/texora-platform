package com.texora.secops.siem.correlation.model;

import com.texora.secops.siem.domain.enums.AlertSeverity;

import java.util.List;

/** The parsed form of {@code SiemCorrelationRule.ruleDefinition} (stored as JSON text). */
public class RuleDefinition {

    private List<RuleCondition> conditions;
    private AlertSeverity severity;

    public RuleDefinition() {
        // required for JSON (de)serialization
    }

    public List<RuleCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<RuleCondition> conditions) {
        this.conditions = conditions;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }
}
