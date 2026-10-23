package com.texora.secops.ldap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Records the approval or rejection decision made by a second authorizer.
 * Lives in {@code ldap.ldap_schema_approval}.
 *
 * <p>The two-person rule: {@code approver_id} must differ from
 * {@link LdapSchemaDefinition#getSubmittedBy()} — enforced by
 * {@link com.texora.secops.ldap.schema.service.SchemaApprovalService}.
 */
@Entity
@Table(name = "ldap_schema_approval", schema = "ldap")
public class LdapSchemaApproval {

    public enum Decision {
        APPROVED, REJECTED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "schema_definition_id", nullable = false)
    private UUID schemaDefinitionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "notes")
    private String notes;

    protected LdapSchemaApproval() {
        // required by JPA
    }

    public LdapSchemaApproval(UUID id, UUID schemaDefinitionId, UUID tenantId,
                               UUID approverId, Decision decision, Instant decidedAt, String notes) {
        this.id = id;
        this.schemaDefinitionId = schemaDefinitionId;
        this.tenantId = tenantId;
        this.approverId = approverId;
        this.decision = decision.name();
        this.decidedAt = decidedAt;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSchemaDefinitionId() {
        return schemaDefinitionId;
    }

    public void setSchemaDefinitionId(UUID schemaDefinitionId) {
        this.schemaDefinitionId = schemaDefinitionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public void setApproverId(UUID approverId) {
        this.approverId = approverId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Decision getDecisionEnum() {
        return Decision.valueOf(this.decision);
    }

    public void setDecisionEnum(Decision decision) {
        this.decision = decision.name();
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
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
        if (!(other instanceof LdapSchemaApproval)) {
            return false;
        }
        LdapSchemaApproval that = (LdapSchemaApproval) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LdapSchemaApproval{id=" + id + ", schemaDefinitionId=" + schemaDefinitionId
                + ", decision='" + decision + "'}";
    }
}
