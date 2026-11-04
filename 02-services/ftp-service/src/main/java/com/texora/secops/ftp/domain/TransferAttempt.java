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
@Table(name = "ftp_transfer_attempt", schema = "ftp")
public class TransferAttempt {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "transfer_job_id", nullable = false, updatable = false)
    private UUID transferJobId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 16, nullable = false, updatable = false)
    private TransferPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 32, nullable = false)
    private AttemptOutcome outcome;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    protected TransferAttempt() { // required by JPA
    }

    public TransferAttempt(UUID id, UUID tenantId, UUID transferJobId, int attemptNumber, TransferPhase phase, AttemptOutcome outcome, Instant startedAt, Instant endedAt, String errorSummary) {
        this.id = id;
        this.tenantId = tenantId;
        this.transferJobId = transferJobId;
        this.attemptNumber = attemptNumber;
        this.phase = phase;
        this.outcome = outcome;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.errorSummary = errorSummary;
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

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public TransferPhase getPhase() {
        return phase;
    }

    public void setPhase(TransferPhase phase) {
        this.phase = phase;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AttemptOutcome outcome) {
        this.outcome = outcome;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransferAttempt)) {
            return false;
        }
        TransferAttempt that = (TransferAttempt) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TransferAttempt{" + "id=" + id + ", transferJobId=" + transferJobId + ", attemptNumber=" + attemptNumber + ", phase=" + phase + ", outcome=" + outcome + "}";
    }
}
