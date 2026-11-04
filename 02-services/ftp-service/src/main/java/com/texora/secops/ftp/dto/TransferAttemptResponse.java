package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.Objects;

public class TransferAttemptResponse {

    private int attemptNumber;

    private String phase;

    private String outcome;

    private Instant startedAt;

    private Instant endedAt;

    private String errorSummary;

    public TransferAttemptResponse() {
    }

    public TransferAttemptResponse(int attemptNumber, String phase, String outcome, Instant startedAt, Instant endedAt, String errorSummary) {
        this.attemptNumber = attemptNumber;
        this.phase = phase;
        this.outcome = outcome;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.errorSummary = errorSummary;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
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
        if (!(other instanceof TransferAttemptResponse)) {
            return false;
        }
        TransferAttemptResponse that = (TransferAttemptResponse) other;
        return Objects.equals(attemptNumber, that.attemptNumber) && Objects.equals(phase, that.phase) && Objects.equals(outcome, that.outcome) && Objects.equals(startedAt, that.startedAt) && Objects.equals(endedAt, that.endedAt) && Objects.equals(errorSummary, that.errorSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptNumber, phase, outcome, startedAt, endedAt, errorSummary);
    }

    @Override
    public String toString() {
        return "TransferAttemptResponse{" + "attemptNumber=" + attemptNumber + ", phase=" + phase + ", outcome=" + outcome + ", startedAt=" + startedAt + ", endedAt=" + endedAt + ", errorSummary=" + errorSummary + "}";
    }
}
