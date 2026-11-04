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
@Table(name = "ftp_transfer_job", schema = "ftp")
public class TransferJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "initiator_sso_subject", nullable = false, updatable = false)
    private UUID initiatorSsoSubject;

    @Column(name = "source_endpoint_id", nullable = false, updatable = false)
    private UUID sourceEndpointId;

    @Column(name = "dest_endpoint_id", nullable = false, updatable = false)
    private UUID destEndpointId;

    @Column(name = "file_name", length = 255, nullable = false, updatable = false)
    private String fileName;

    @Column(name = "require_inspection", nullable = false, updatable = false)
    private boolean requireInspection;

    @Column(name = "expected_sha256", length = 64, updatable = false)
    private String expectedSha256;

    @Column(name = "staging_uri", length = 1024)
    private String stagingUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private JobStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransferJob() { // required by JPA
    }

    public TransferJob(UUID id, UUID tenantId, UUID initiatorSsoSubject, UUID sourceEndpointId, UUID destEndpointId, String fileName, boolean requireInspection, String expectedSha256, String stagingUri, JobStatus status, String failureReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.initiatorSsoSubject = initiatorSsoSubject;
        this.sourceEndpointId = sourceEndpointId;
        this.destEndpointId = destEndpointId;
        this.fileName = fileName;
        this.requireInspection = requireInspection;
        this.expectedSha256 = expectedSha256;
        this.stagingUri = stagingUri;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public UUID getInitiatorSsoSubject() {
        return initiatorSsoSubject;
    }

    public void setInitiatorSsoSubject(UUID initiatorSsoSubject) {
        this.initiatorSsoSubject = initiatorSsoSubject;
    }

    public UUID getSourceEndpointId() {
        return sourceEndpointId;
    }

    public void setSourceEndpointId(UUID sourceEndpointId) {
        this.sourceEndpointId = sourceEndpointId;
    }

    public UUID getDestEndpointId() {
        return destEndpointId;
    }

    public void setDestEndpointId(UUID destEndpointId) {
        this.destEndpointId = destEndpointId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isRequireInspection() {
        return requireInspection;
    }

    public void setRequireInspection(boolean requireInspection) {
        this.requireInspection = requireInspection;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public void setExpectedSha256(String expectedSha256) {
        this.expectedSha256 = expectedSha256;
    }

    public String getStagingUri() {
        return stagingUri;
    }

    public void setStagingUri(String stagingUri) {
        this.stagingUri = stagingUri;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
        if (!(other instanceof TransferJob)) {
            return false;
        }
        TransferJob that = (TransferJob) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TransferJob{" + "id=" + id + ", tenantId=" + tenantId + ", status=" + status + ", initiatorSsoSubject=" + initiatorSsoSubject + "}";
    }
}
