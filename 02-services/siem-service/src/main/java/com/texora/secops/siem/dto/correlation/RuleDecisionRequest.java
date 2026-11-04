package com.texora.secops.siem.dto.correlation;

import jakarta.validation.constraints.NotNull;

public class RuleDecisionRequest {

    public enum Decision {
        APPROVE,
        REJECT
    }

    @NotNull
    private Decision decision;

    private String notes;

    public RuleDecisionRequest() {
        // required for JSON deserialization
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
