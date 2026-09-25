package com.texora.secops.sec.secrets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A secret reference — vault path and KMS key ID ONLY.
 *
 * <p>This table contains NO secret material. It maps logical names to vault paths
 * and KMS key IDs for each consuming service. The consuming service retrieves the
 * actual material from the vault/KMS using its OWN mTLS identity.</p>
 *
 * <p>Read access is scoped to the owning_service (enforced in SecretReferenceService).
 * A caller that is not the owner receives 403 Forbidden (Req 2 from prompt).</p>
 */
@Entity
@Table(name = "sec_secret_reference", schema = "sec")
public class SecSecretReference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "logical_name", nullable = false, length = 255)
    private String logicalName;

    @Column(name = "vault_path", nullable = false, length = 512)
    private String vaultPath;

    @Column(name = "kms_key_id", length = 512)
    private String kmsKeyId;

    @Column(name = "owning_service", nullable = false, length = 64)
    private String owningService;

    @Column(name = "classification", nullable = false, length = 64)
    private String classification;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SecSecretReference() {
        // required by JPA
    }

    public SecSecretReference(UUID id, UUID tenantId, String logicalName, String vaultPath,
                               String kmsKeyId, String owningService, String classification) {
        this.id = id;
        this.tenantId = tenantId;
        this.logicalName = logicalName;
        this.vaultPath = vaultPath;
        this.kmsKeyId = kmsKeyId;
        this.owningService = owningService;
        this.classification = classification;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }

    public String getVaultPath() { return vaultPath; }
    public void setVaultPath(String vaultPath) { this.vaultPath = vaultPath; }

    public String getKmsKeyId() { return kmsKeyId; }
    public void setKmsKeyId(String kmsKeyId) { this.kmsKeyId = kmsKeyId; }

    public String getOwningService() { return owningService; }
    public void setOwningService(String owningService) { this.owningService = owningService; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecSecretReference)) return false;
        SecSecretReference that = (SecSecretReference) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "SecSecretReference{id=" + id + ", logicalName='" + logicalName
                + "', owningService='" + owningService + "', status='" + status + "'}";
    }
}
