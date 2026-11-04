package com.texora.secops.ftp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ftp_quarantine_record", schema = "ftp")
public class QuarantineRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "transfer_job_id", nullable = false, updatable = false)
    private UUID transferJobId;

    @Column(name = "reason", length = 500, nullable = false, updatable = false)
    private String reason;

    @Column(name = "quarantined_at", nullable = false, updatable = false)
    private Instant quarantinedAt;

    @Column(name = "released_by")
    private UUID releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private QuarantineStatus status;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    protected QuarantineRecord() { // required by JPA
    }

    public QuarantineRecord(UUID id, UUID tenantId, UUID transferJobId, String reason, Instant quarantinedAt, UUID releasedBy, Instant releasedAt, QuarantineStatus status, String resolutionNote) {
        this.id = id;
        this.tenantId = tenantId;
        this.transferJobId = transferJobId;
        this.reason = reason;
        this.quarantinedAt = quarantinedAt;
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
        this.status = status;
        this.resolutionNote = resolutionNote;
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

    public UUID getTransferJobId() {
        return transferJobId;
    }

    public void setTransferJobId(UUID transferJobId) {
        this.transferJobId = transferJobId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getQuarantinedAt() {
        return quarantinedAt;
    }

    public void setQuarantinedAt(Instant quarantinedAt) {
        this.quarantinedAt = quarantinedAt;
    }

    public UUID getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(UUID releasedBy) {
        this.releasedBy = releasedBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public QuarantineStatus getStatus() {
        return status;
    }

    public void setStatus(QuarantineStatus status) {
        this.status = status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuarantineRecord)) {
            return false;
        }
        QuarantineRecord that = (QuarantineRecord) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "QuarantineRecord{" + "id=" + id + ", transferJobId=" + transferJobId + ", status=" + status + "}";
    }
}
