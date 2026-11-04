package com.texora.qap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Points to a SEC secret reference for test-environment credentials.
 * NO credential material is ever stored here — only the opaque reference ID
 * issued by the Security Server (SEC).  The actual material is resolved
 * at the point of use by CredentialReferenceService via mTLS to SEC.
 */
@Entity
@Table(name = "qap_credential_reference", schema = "qap")
public class QapCredentialReference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false, length = 128)
    private String productId;

    /**
     * Opaque reference ID issued by SEC — this is a pointer, NEVER plaintext credentials.
     * Shared Standards B.6.6 and Requirement 3 from the QAP prompt.
     */
    @Column(name = "sec_secret_reference_id", nullable = false, length = 256)
    private String secSecretReferenceId;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QapCredentialReference() {
        // required by JPA
    }

    public QapCredentialReference(UUID id, UUID tenantId, String productId,
                                   String secSecretReferenceId, String description,
                                   Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.secSecretReferenceId = secSecretReferenceId;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSecSecretReferenceId() { return secSecretReferenceId; }
    public void setSecSecretReferenceId(String secSecretReferenceId) { this.secSecretReferenceId = secSecretReferenceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QapCredentialReference)) return false;
        QapCredentialReference that = (QapCredentialReference) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "QapCredentialReference{id=" + id + ", tenantId=" + tenantId
               + ", productId='" + productId + "'}";
    }
}
