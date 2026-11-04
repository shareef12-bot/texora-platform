package com.texora.secops.ldap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A protected LDAP bind endpoint configuration.
 * Lives in {@code ldap.ldap_bind_config}.
 *
 * <p>Security notes:
 * <ul>
 *   <li>No anonymous bind: TLS is required by default ({@code tls_required = true}).</li>
 *   <li>{@code allowed_source} restricts which callers may use this bind DN.</li>
 *   <li>Bind credentials are NOT stored here — they are retrieved from SEC at runtime.</li>
 *   <li>Mutations require {@code ldap.bind.write} role and are always audited.</li>
 * </ul>
 */
@Entity
@Table(name = "ldap_bind_config", schema = "ldap")
public class LdapBindConfig {

    public enum Status {
        ACTIVE, DISABLED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Bind DN (e.g. {@code cn=svc-sso,ou=services,dc=texora,dc=internal}). */
    @Column(name = "bind_dn", nullable = false, length = 512)
    private String bindDn;

    /** If true, the LDAP engine must refuse connections that are not over TLS. */
    @Column(name = "tls_required", nullable = false)
    private boolean tlsRequired;

    /**
     * CIDR or hostname pattern restricting which callers may use this bind DN.
     * {@code null} means no restriction is configured — treat as WIDE OPEN and alert.
     */
    @Column(name = "allowed_source", length = 512)
    private String allowedSource;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected LdapBindConfig() {
        // required by JPA
    }

    public LdapBindConfig(UUID id, UUID tenantId, String bindDn, boolean tlsRequired,
                           String allowedSource, Status status, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.bindDn = bindDn;
        this.tlsRequired = tlsRequired;
        this.allowedSource = allowedSource;
        this.status = status.name();
        this.createdBy = createdBy;
        this.createdAt = createdAt;
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

    public String getBindDn() {
        return bindDn;
    }

    public void setBindDn(String bindDn) {
        this.bindDn = bindDn;
    }

    public boolean isTlsRequired() {
        return tlsRequired;
    }

    public void setTlsRequired(boolean tlsRequired) {
        this.tlsRequired = tlsRequired;
    }

    public String getAllowedSource() {
        return allowedSource;
    }

    public void setAllowedSource(String allowedSource) {
        this.allowedSource = allowedSource;
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

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
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
        if (!(other instanceof LdapBindConfig)) {
            return false;
        }
        LdapBindConfig that = (LdapBindConfig) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LdapBindConfig{id=" + id + ", bindDn='" + bindDn + "', status='" + status + "'}";
    }
}
