package com.texora.secops.cgi.execution.adapter;

/**
 * THE single seam for ALL legacy-handler execution (Shared Standards B.4; ADR-D5). It is the ONLY place in the
 * service allowed to start or stop a worker; nothing else may use ProcessBuilder / Runtime.exec / a container API
 * (enforced by {@code AdapterBoundaryTest}).
 *
 * <p>Contract every real implementation MUST honour:
 * <ul>
 *   <li>{@link #spawn}: run ONLY the handler named in the {@link IsolationSpec}; sandboxed, non-root, no network
 *       unless the spec explicitly (and, upstream, two-person-approved) says otherwise; refuse - by throwing
 *       {@link WorkerSpawnException} - anything it cannot isolate as specified. Never fall back to weaker isolation.</li>
 *   <li>{@link #kill}: FORCIBLY terminate the worker and everything it forked (SIGKILL the whole process group /
 *       delete the container), release resources, be idempotent, and treat an already-dead worker as success.
 *       SIGTERM-ignoring handlers must still die. Throw only if death cannot be confirmed.</li>
 *   <li>Never put request parameters, handler output or secrets into exception messages or logs.</li>
 * </ul>
 */
public interface WorkerIsolationAdapter {

    /** True if this adapter knows how to run the handler id. Used to reject allowlist entries for unknown handlers. */
    boolean supportsHandler(String handlerId);

    WorkerHandle spawn(WorkerSpawnRequest request);

    void kill(WorkerHandle handle);
}
