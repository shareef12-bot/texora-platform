package com.texora.secops.sec.secrets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sec_key_rotation_record", schema = "sec")
public class SecKeyRotationRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "secret_reference_id", nullable = false)
    private UUID secretReferenceId;

    @Column(name = "rotation_due_at", nullable = false)
    private Instant rotationDueAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "rotated_by")
    private UUID rotatedBy;

    @Column(name = "outcome", length = 32)
    private String outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SecKeyRotationRecord() {
        // required by JPA
    }

    public SecKeyRotationRecord(UUID id, UUID tenantId, UUID secretReferenceId, Instant rotationDueAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.secretReferenceId = secretReferenceId;
        this.rotationDueAt = rotationDueAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSecretReferenceId() { return secretReferenceId; }
    public void setSecretReferenceId(UUID secretReferenceId) { this.secretReferenceId = secretReferenceId; }

    public Instant getRotationDueAt() { return rotationDueAt; }
    public void setRotationDueAt(Instant rotationDueAt) { this.rotationDueAt = rotationDueAt; }

    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }

    public UUID getRotatedBy() { return rotatedBy; }
    public void setRotatedBy(UUID rotatedBy) { this.rotatedBy = rotatedBy; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecKeyRotationRecord)) return false;
        SecKeyRotationRecord that = (SecKeyRotationRecord) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "SecKeyRotationRecord{id=" + id + ", secretReferenceId=" + secretReferenceId
                + ", rotationDueAt=" + rotationDueAt + ", outcome='" + outcome + "'}";
    }
}
