package com.texora.secops.cgi.execution.adapter;

import java.util.concurrent.CompletableFuture;

/** Opaque reference to one spawned worker. Created and understood only by a {@link WorkerIsolationAdapter}. */
public interface WorkerHandle {

    String workerId();

    /** Completes with the handler's result; completes exceptionally if the worker is killed or crashes. */
    CompletableFuture<WorkerResult> completion();
}
