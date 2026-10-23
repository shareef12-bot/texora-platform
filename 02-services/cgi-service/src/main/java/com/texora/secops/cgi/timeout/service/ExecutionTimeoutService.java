package com.texora.secops.cgi.timeout.service;

import com.texora.secops.cgi.execution.adapter.WorkerHandle;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.execution.adapter.WorkerResult;
import com.texora.secops.cgi.execution.domain.ExecutionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Starts the timer at the route's limit and, on expiry, has {@link WorkerIsolationAdapter} FORCIBLY KILL the
 * worker (CGI-F-009). The worker is killed in a {@code finally} on EVERY path - timeout, handler error,
 * interruption, even a normal finish (to reap anything it forked) - and the kill happens BEFORE this method returns,
 * i.e. before the caller is answered. If a kill cannot be confirmed the outcome is never COMPLETED.
 */
@Service
public class ExecutionTimeoutService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionTimeoutService.class);

    private final WorkerIsolationAdapter adapter;

    public ExecutionTimeoutService(WorkerIsolationAdapter adapter) {
        this.adapter = adapter;
    }

    public SupervisionResult supervise(WorkerHandle handle, Duration limit) {
        ExecutionOutcome outcome = ExecutionOutcome.FAILED;
        String reason = "SUPERVISION_ERROR";
        WorkerResult result = null;
        boolean killConfirmed = false;
        try {
            if (handle == null || limit == null || limit.isZero() || limit.isNegative()) {
                reason = "INVALID_SUPERVISION_INPUT";
            } else {
                try {
                    result = handle.completion().get(limit.toMillis(), TimeUnit.MILLISECONDS);
                    if (result == null) {
                        reason = "NULL_RESULT";
                    } else {
                        outcome = ExecutionOutcome.COMPLETED;
                        reason = "EXIT_" + result.getExitCode();
                    }
                } catch (TimeoutException e) {
                    outcome = ExecutionOutcome.TIMED_OUT;
                    reason = "TIMEOUT_KILLED";
                    result = null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    reason = "INTERRUPTED";
                } catch (ExecutionException | RuntimeException e) {
                    reason = "WORKER_ERROR";
                }
            }
        } finally {
            killConfirmed = killQuietly(handle);
        }
        if (!killConfirmed) {
            LOGGER.error("CGI_KILL_NOT_CONFIRMED worker={} outcome={} - treating as failure",
                    handle == null ? null : handle.workerId(), outcome);
            reason = reason + "+KILL_FAILED";
            result = null;
            if (outcome != ExecutionOutcome.TIMED_OUT) {
                outcome = ExecutionOutcome.FAILED;
            }
        }
        return new SupervisionResult(outcome, reason, outcome == ExecutionOutcome.COMPLETED ? result : null);
    }

    /** Idempotent forced kill. @return true if the adapter confirmed (did not throw). */
    public boolean killQuietly(WorkerHandle handle) {
        if (handle == null) {
            return true;
        }
        try {
            adapter.kill(handle);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Forced kill of worker {} failed ({})", handle.workerId(), e.getClass().getName());
            return false;
        }
    }
}
