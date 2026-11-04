package com.texora.secops.cgi.timeout;

import com.texora.secops.cgi.execution.adapter.WorkerHandle;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.execution.adapter.WorkerResult;
import com.texora.secops.cgi.execution.domain.ExecutionOutcome;
import com.texora.secops.cgi.timeout.service.ExecutionTimeoutService;
import com.texora.secops.cgi.timeout.service.SupervisionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ExecutionTimeoutServiceTest {

    private final WorkerIsolationAdapter adapter = mock(WorkerIsolationAdapter.class);
    private final ExecutionTimeoutService service = new ExecutionTimeoutService(adapter);

    private static final class Handle implements WorkerHandle {
        final CompletableFuture<WorkerResult> future = new CompletableFuture<>();

        @Override
        public String workerId() {
            return "w-1";
        }

        @Override
        public CompletableFuture<WorkerResult> completion() {
            return future;
        }
    }

    @Test
    void completedWorkerIsStillKilledToReapChildren() {
        Handle h = new Handle();
        h.future.complete(new WorkerResult(0, 200, "text/plain", new byte[]{1}));
        SupervisionResult r = service.supervise(h, Duration.ofSeconds(2));
        assertThat(r.getOutcome()).isEqualTo(ExecutionOutcome.COMPLETED);
        assertThat(r.getResult()).isNotNull();
        verify(adapter).kill(h);
    }

    @Test
    void timeoutForcesKillAndRecordsTimedOut() {
        Handle h = new Handle();
        SupervisionResult r = service.supervise(h, Duration.ofMillis(80));
        assertThat(r.getOutcome()).isEqualTo(ExecutionOutcome.TIMED_OUT);
        assertThat(r.getReason()).isEqualTo("TIMEOUT_KILLED");
        assertThat(r.getResult()).isNull();
        verify(adapter, times(1)).kill(h);
    }

    @Test
    void workerErrorIsFailedAndKilled() {
        Handle h = new Handle();
        h.future.completeExceptionally(new IllegalStateException("crash"));
        SupervisionResult r = service.supervise(h, Duration.ofSeconds(1));
        assertThat(r.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        verify(adapter).kill(h);
    }

    @Test
    void killFailureOnTimeoutStaysTimedOutButIsFlagged() {
        Handle h = new Handle();
        doThrow(new IllegalStateException("cannot kill")).when(adapter).kill(any());
        SupervisionResult r = service.supervise(h, Duration.ofMillis(50));
        assertThat(r.getOutcome()).isEqualTo(ExecutionOutcome.TIMED_OUT);
        assertThat(r.getReason()).contains("KILL_FAILED");
    }

    @Test
    void completedButUnkillableWorkerIsNeverTreatedAsSuccess() {
        Handle h = new Handle();
        h.future.complete(new WorkerResult(0, 200, "text/plain", new byte[0]));
        doThrow(new IllegalStateException("cannot kill")).when(adapter).kill(any());
        SupervisionResult r = service.supervise(h, Duration.ofSeconds(1));
        assertThat(r.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(r.getResult()).isNull();
    }

    @Test
    void invalidLimitFailsClosedAndKills() {
        Handle h = new Handle();
        assertThat(service.supervise(h, Duration.ZERO).getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(service.supervise(h, null).getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        verify(adapter, times(2)).kill(h);
    }

    @Test
    void interruptionFailsClosedKillsAndPreservesInterruptFlag() {
        Handle h = new Handle();
        Thread.currentThread().interrupt();
        try {
            SupervisionResult r = service.supervise(h, Duration.ofSeconds(5));
            assertThat(r.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(adapter).kill(h);
        } finally {
            Thread.interrupted(); // clear for other tests
        }
    }

    @Test
    void nullHandleIsFailedWithoutError() {
        assertThat(service.supervise(null, Duration.ofSeconds(1)).getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
    }

    @Test
    void killQuietlyReportsFailure() {
        Handle h = new Handle();
        assertThat(service.killQuietly(h)).isTrue();
        doThrow(new IllegalStateException()).when(adapter).kill(any());
        assertThat(service.killQuietly(h)).isFalse();
    }
}
