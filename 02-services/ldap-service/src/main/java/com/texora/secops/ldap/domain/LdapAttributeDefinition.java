package com.texora.secops.ldap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * An attribute definition belonging to a schema object class.
 * Lives in {@code ldap.ldap_attribute_definition}.
 */
@Entity
@Table(name = "ldap_attribute_definition", schema = "ldap")
public class LdapAttributeDefinition {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "schema_definition_id", nullable = false)
    private UUID schemaDefinitionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    /**
     * LDAP attribute syntax OID (e.g. {@code 1.3.6.1.4.1.1466.115.121.1.15} for DirectoryString).
     * Validated against a whitelist before acceptance.
     */
    @Column(name = "syntax", nullable = false, length = 256)
    private String syntax;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    protected LdapAttributeDefinition() {
        // required by JPA
    }

    public LdapAttributeDefinition(UUID id, UUID schemaDefinitionId, UUID tenantId,
                                    String name, String syntax, boolean required) {
        this.id = id;
        this.schemaDefinitionId = schemaDefinitionId;
        this.tenantId = tenantId;
        this.name = name;
        this.syntax = syntax;
        this.required = required;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSyntax() {
        return syntax;
    }

    public void setSyntax(String syntax) {
        this.syntax = syntax;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LdapAttributeDefinition)) {
            return false;
        }
        LdapAttributeDefinition that = (LdapAttributeDefinition) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LdapAttributeDefinition{id=" + id + ", name='" + name + "', syntax='" + syntax + "'}";
    }
}
