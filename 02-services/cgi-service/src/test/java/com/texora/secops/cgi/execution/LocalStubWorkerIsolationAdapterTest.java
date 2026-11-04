package com.texora.secops.cgi.execution;

import com.texora.secops.cgi.execution.adapter.IsolationSpec;
import com.texora.secops.cgi.execution.adapter.LocalStubWorkerIsolationAdapter;
import com.texora.secops.cgi.execution.adapter.WorkerHandle;
import com.texora.secops.cgi.execution.adapter.WorkerResult;
import com.texora.secops.cgi.execution.adapter.WorkerSpawnException;
import com.texora.secops.cgi.execution.adapter.WorkerSpawnRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Adversarial checks against the contract the REAL adapter must also meet. */
class LocalStubWorkerIsolationAdapterTest {

    private final LocalStubWorkerIsolationAdapter adapter = new LocalStubWorkerIsolationAdapter(new MockEnvironment());

    private WorkerSpawnRequest request(String handler, boolean network, boolean nonRoot) {
        return new WorkerSpawnRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new IsolationSpec(handler, "strict-default", network, nonRoot), "GET", "/x", null, Map.of(), null);
    }

    @Test
    void echoCompletes() throws Exception {
        WorkerHandle h = adapter.spawn(request("stub-echo", false, true));
        WorkerResult r = h.completion().get(2, TimeUnit.SECONDS);
        assertThat(r.getHttpStatus()).isEqualTo(200);
        adapter.kill(h);
    }

    @Test
    void hangingWorkerIsKilledAndNoLateResultCanArrive() throws Exception {
        WorkerHandle h = adapter.spawn(request("stub-hang", false, true));
        adapter.kill(h);
        assertThatThrownBy(() -> h.completion().get(1, TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(h.completion().complete(new WorkerResult(0, 200, "x/y", null))).isFalse();
    }

    @Test
    void workerIgnoringSigtermStillDiesOnForcedKill() throws Exception {
        WorkerHandle h = adapter.spawn(request("stub-ignore-sigterm", false, true));
        Thread.sleep(100);
        assertThat(h.completion().isDone()).isFalse();
        adapter.kill(h);
        assertThat(h.completion().isCompletedExceptionally()).isTrue();
        // the simulated thread must actually terminate
        long deadline = System.currentTimeMillis() + 2000;
        Thread worker = findWorkerThread(h.workerId());
        while (worker != null && worker.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(worker == null || !worker.isAlive()).isTrue();
    }

    @Test
    void forkAndOutboundNetworkAttemptsAreDeniedNotExecuted() throws Exception {
        for (String handler : new String[]{"stub-fork", "stub-network"}) {
            WorkerHandle h = adapter.spawn(request(handler, false, true));
            WorkerResult r = h.completion().get(2, TimeUnit.SECONDS);
            assertThat(r.getExitCode()).isEqualTo(126);
            adapter.kill(h);
        }
    }

    @Test
    void relaxedIsolationIsRefused() {
        assertThatThrownBy(() -> adapter.spawn(request("stub-echo", true, true))).isInstanceOf(WorkerSpawnException.class);
        assertThatThrownBy(() -> adapter.spawn(request("stub-echo", false, false))).isInstanceOf(WorkerSpawnException.class);
    }

    @Test
    void unknownHandlerIsRefused() {
        assertThat(adapter.supportsHandler("rm-rf")).isFalse();
        assertThat(adapter.supportsHandler(null)).isFalse();
        assertThatThrownBy(() -> adapter.spawn(request("rm-rf", false, true))).isInstanceOf(WorkerSpawnException.class);
    }

    @Test
    void refusesToStartInProduction() {
        LocalStubWorkerIsolationAdapter prod = new LocalStubWorkerIsolationAdapter(new MockEnvironment() {
            {
                setActiveProfiles("prod");
            }
        });
        assertThatThrownBy(prod::guardAndWarn).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void killRejectsForeignHandles() {
        assertThatThrownBy(() -> adapter.kill(new WorkerHandle() {
            public String workerId() {
                return "x";
            }

            public java.util.concurrent.CompletableFuture<WorkerResult> completion() {
                return new java.util.concurrent.CompletableFuture<>();
            }
        })).isInstanceOf(IllegalArgumentException.class);
    }

    private static Thread findWorkerThread(String workerId) {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (("cgi-stub-worker-" + workerId).equals(t.getName())) {
                return t;
            }
        }
        return null;
    }
}
