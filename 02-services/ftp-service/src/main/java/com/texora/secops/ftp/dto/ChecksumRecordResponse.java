package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ChecksumRecordResponse {

    private UUID id;

    private UUID transferJobId;

    private String algorithm;

    private String expectedValue;

    private String computedValue;

    private boolean verified;

    private Instant recordedAt;

    public ChecksumRecordResponse() {
    }

    public ChecksumRecordResponse(UUID id, UUID transferJobId, String algorithm, String expectedValue, String computedValue, boolean verified, Instant recordedAt) {
        this.id = id;
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
        if (!(other instanceof ChecksumRecordResponse)) {
            return false;
        }
        ChecksumRecordResponse that = (ChecksumRecordResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(transferJobId, that.transferJobId) && Objects.equals(algorithm, that.algorithm) && Objects.equals(expectedValue, that.expectedValue) && Objects.equals(computedValue, that.computedValue) && Objects.equals(verified, that.verified) && Objects.equals(recordedAt, that.recordedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transferJobId, algorithm, expectedValue, computedValue, verified, recordedAt);
    }

    @Override
    public String toString() {
        return "ChecksumRecordResponse{" + "id=" + id + ", transferJobId=" + transferJobId + ", algorithm=" + algorithm + ", expectedValue=" + expectedValue + ", computedValue=" + computedValue + ", verified=" + verified + ", recordedAt=" + recordedAt + "}";
    }
}
