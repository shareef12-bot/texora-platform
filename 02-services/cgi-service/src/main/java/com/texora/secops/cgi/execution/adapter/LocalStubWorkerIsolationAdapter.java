package com.texora.secops.cgi.execution.adapter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <b>LOCAL STUB - NOT REAL ISOLATION. NOT FOR PRODUCTION.</b>
 *
 * <p>The list of real legacy handlers and the isolation runtime are not decided yet (ADR-D5, LLD section 12). Until
 * they are, this stub stands behind {@link WorkerIsolationAdapter} so the gateway, allowlist, timeout and audit
 * machinery can be built and tested. It runs NO real handler and NO operating-system process: it simulates a few
 * dummy handlers on daemon threads so the adversarial paths (hang, ignore-SIGTERM, fork, outbound network) are
 * testable. "Kill" here only models the contract (the future is failed immediately so no late result can ever be
 * delivered); a real adapter must really kill the process tree / container.
 *
 * <p>It refuses to start under a {@code prod}/{@code production} profile, and refuses any spec that is not
 * no-network + non-root.
 */
@Component
@ConditionalOnProperty(prefix = "cgi.execution", name = "adapter", havingValue = "stub", matchIfMissing = true)
public class LocalStubWorkerIsolationAdapter implements WorkerIsolationAdapter {

    public static final String HANDLER_ECHO = "stub-echo";
    public static final String HANDLER_HANG = "stub-hang";
    public static final String HANDLER_IGNORE_SIGTERM = "stub-ignore-sigterm";
    public static final String HANDLER_FORK = "stub-fork";
    public static final String HANDLER_NETWORK = "stub-network";

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStubWorkerIsolationAdapter.class);
    private static final Set<String> HANDLERS = Set.of(HANDLER_ECHO, HANDLER_HANG, HANDLER_IGNORE_SIGTERM, HANDLER_FORK, HANDLER_NETWORK);

    private final Environment environment;
    private final Map<String, StubWorkerHandle> live = new ConcurrentHashMap<>();

    public LocalStubWorkerIsolationAdapter(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void guardAndWarn() {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException("LocalStubWorkerIsolationAdapter must never run in production: "
                    + "configure a real WorkerIsolationAdapter (ADR-D5) and set cgi.execution.adapter accordingly");
        }
        LOGGER.warn("**************************************************************************************");
        LOGGER.warn("* LOCAL STUB WorkerIsolationAdapter ACTIVE - NO REAL SANDBOXING, NO REAL LEGACY HANDLERS *");
        LOGGER.warn("* Not for production. Real handler inventory + isolation runtime are open (ADR-D5).      *");
        LOGGER.warn("**************************************************************************************");
    }

    @Override
    public boolean supportsHandler(String handlerId) {
        return handlerId != null && HANDLERS.contains(handlerId);
    }

    @Override
    public WorkerHandle spawn(WorkerSpawnRequest request) {
        if (request == null || !supportsHandler(request.getHandlerId())) {
            throw new WorkerSpawnException("UNKNOWN_HANDLER");
        }
        IsolationSpec spec = request.getIsolation();
        if (spec == null || spec.isNetworkAllowed() || !spec.isRunAsNonRoot()) {
            throw new WorkerSpawnException("STUB_REFUSES_RELAXED_ISOLATION");
        }
        LOGGER.warn("STUB worker spawn (not real isolation) handler={}", request.getHandlerId());
        StubWorkerHandle worker = new StubWorkerHandle(UUID.randomUUID().toString(), request.getHandlerId());
        live.put(worker.workerId(), worker);
        Thread thread = new Thread(() -> run(worker, request), "cgi-stub-worker-" + worker.workerId());
        thread.setDaemon(true);
        worker.thread = thread;
        thread.start();
        return worker;
    }

    @Override
    public void kill(WorkerHandle handle) {
        if (!(handle instanceof StubWorkerHandle worker)) {
            throw new IllegalArgumentException("Handle was not created by this adapter");
        }
        worker.killed = true;                                              // the simulated SIGKILL
        worker.future.completeExceptionally(new CancellationException("worker killed")); // no late result possible
        Thread t = worker.thread;
        if (t != null) {
            t.interrupt();
        }
        live.remove(worker.workerId());
    }

    private void run(StubWorkerHandle worker, WorkerSpawnRequest request) {
        switch (worker.handlerId) {
            case HANDLER_ECHO -> {
                String json = "{\"handler\":\"stub-echo\",\"stub\":true,\"parameterCount\":" + request.getParameters().size() + "}";
                worker.future.complete(new WorkerResult(0, 200, "application/json", json.getBytes(StandardCharsets.UTF_8)));
            }
            case HANDLER_HANG -> sleepUntilKilled(worker, false);
            case HANDLER_IGNORE_SIGTERM -> sleepUntilKilled(worker, true);
            case HANDLER_FORK -> {
                worker.deniedActions.add("FORK");
                worker.future.complete(new WorkerResult(126, 500, "text/plain", "denied".getBytes(StandardCharsets.UTF_8)));
            }
            case HANDLER_NETWORK -> {
                worker.deniedActions.add("NETWORK_EGRESS");
                worker.future.complete(new WorkerResult(126, 500, "text/plain", "denied".getBytes(StandardCharsets.UTF_8)));
            }
            default -> worker.future.completeExceptionally(new IllegalStateException("unknown stub handler"));
        }
    }

    /** @param ignoreSigterm simulates a handler that swallows the polite stop signal (interrupt) and only dies on the hard kill. */
    private static void sleepUntilKilled(StubWorkerHandle worker, boolean ignoreSigterm) {
        while (!worker.killed) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                if (!ignoreSigterm) {
                    return;
                }
                // SIGTERM ignored: keep running until the hard kill flag is set.
            }
        }
    }

    /** Simulated worker. Package-private accessors exist for the adversarial tests. */
    static final class StubWorkerHandle implements WorkerHandle {

        private final String workerId;
        private final String handlerId;
        final CompletableFuture<WorkerResult> future = new CompletableFuture<>();
        final Set<String> deniedActions = new CopyOnWriteArraySet<>();
        volatile boolean killed;
        volatile Thread thread;

        StubWorkerHandle(String workerId, String handlerId) {
            this.workerId = workerId;
            this.handlerId = handlerId;
        }

        @Override
        public String workerId() {
            return workerId;
        }

        @Override
        public CompletableFuture<WorkerResult> completion() {
            return future;
        }

        boolean isKilled() {
            return killed;
        }

        Set<String> getDeniedActions() {
            return Collections.unmodifiableSet(deniedActions);
        }

        Thread getThread() {
            return thread;
        }
    }
}
