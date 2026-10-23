package com.texora.secops.cgi.timeout.service;

import com.texora.secops.cgi.execution.adapter.WorkerResult;
import com.texora.secops.cgi.execution.domain.ExecutionOutcome;

/** Outcome of supervising one worker. {@code reason} is a short code, never handler output. */
public final class SupervisionResult {

    private final ExecutionOutcome outcome;
    private final String reason;
    private final WorkerResult result;

    public SupervisionResult(ExecutionOutcome outcome, String reason, WorkerResult result) {
        this.outcome = outcome;
        this.reason = reason;
        this.result = result;
    }

    public static SupervisionResult failed(String reason) {
        return new SupervisionResult(ExecutionOutcome.FAILED, reason, null);
    }

    public ExecutionOutcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public WorkerResult getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "SupervisionResult{outcome=" + outcome + ", reason=" + reason + "}";
    }
}
