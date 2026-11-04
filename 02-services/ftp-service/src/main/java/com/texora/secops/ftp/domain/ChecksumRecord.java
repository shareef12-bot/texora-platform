package com.texora.secops.ftp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ftp_checksum_record", schema = "ftp")
public class ChecksumRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "transfer_job_id", nullable = false, updatable = false)
    private UUID transferJobId;

    @Column(name = "algorithm", length = 32, nullable = false, updatable = false)
    private String algorithm;

    @Column(name = "expected_value", length = 128, nullable = false, updatable = false)
    private String expectedValue;

    @Column(name = "computed_value", length = 128, nullable = false)
    private String computedValue;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ChecksumRecord() { // required by JPA
    }

    public ChecksumRecord(UUID id, UUID tenantId, UUID transferJobId, String algorithm, String expectedValue, String computedValue, boolean verified, Instant recordedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.transferJobId = transferJobId;
        this.algorithm = algorithm;
        this.expectedValue = expectedValue;
        this.computedValue = computedValue;
        this.verified = verified;
        this.recordedAt = recordedAt;
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

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public String getComputedValue() {
        return computedValue;
    }

    public void setComputedValue(String computedValue) {
        this.computedValue = computedValue;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChecksumRecord)) {
            return false;
        }
        ChecksumRecord that = (ChecksumRecord) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ChecksumRecord{" + "id=" + id + ", transferJobId=" + transferJobId + ", algorithm=" + algorithm + ", verified=" + verified + "}";
    }
}
