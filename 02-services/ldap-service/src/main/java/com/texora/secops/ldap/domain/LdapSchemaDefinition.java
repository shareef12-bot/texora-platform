package com.texora.secops.ldap.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Control-plane record representing a proposed LDAP object-class schema change.
 * Lives in the {@code ldap.ldap_schema_definition} table.
 *
 * <p>Status flow: DRAFT → PENDING_APPROVAL → APPROVED | REJECTED.
 * The two-person approval rule is enforced by {@link com.texora.secops.ldap.schema.service.SchemaApprovalService}.
 */
@Entity
@Table(name = "ldap_schema_definition", schema = "ldap")
public class LdapSchemaDefinition {

    /** Status values for schema change workflow. */
    public enum Status {
        DRAFT, PENDING_APPROVAL, APPROVED, REJECTED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "object_class", nullable = false, length = 256)
    private String objectClass;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @OneToMany(mappedBy = "schemaDefinitionId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LdapAttributeDefinition> attributes = new ArrayList<>();

    protected LdapSchemaDefinition() {
        // required by JPA
    }

    public LdapSchemaDefinition(UUID id, UUID tenantId, String objectClass,
                                 Status status, UUID submittedBy, Instant submittedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.objectClass = objectClass;
        this.status = status.name();
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
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

    public String getObjectClass() {
        return objectClass;
    }

    public void setObjectClass(String objectClass) {
        this.objectClass = objectClass;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Status getStatusEnum() {
        return Status.valueOf(this.status);
    }

    public void setStatusEnum(Status status) {
        this.status = status.name();
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public List<LdapAttributeDefinition> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<LdapAttributeDefinition> attributes) {
        this.attributes = attributes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LdapSchemaDefinition)) {
            return false;
        }
        LdapSchemaDefinition that = (LdapSchemaDefinition) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LdapSchemaDefinition{id=" + id + ", objectClass='" + objectClass
                + "', status='" + status + "', tenantId=" + tenantId + "}";
    }
}
