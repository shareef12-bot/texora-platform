package com.texora.secops.ldap.schema.dto;

/**
 * Request body for recording an approval or rejection decision on a schema definition.
 * {@code decision} must be {@code "APPROVED"} or {@code "REJECTED"}.
 */
public class SchemaDecisionRequest {

    private String decision;
    private String notes;

    public SchemaDecisionRequest() {
    }

    public SchemaDecisionRequest(String decision, String notes) {
        this.decision = decision;
        this.notes = notes;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
