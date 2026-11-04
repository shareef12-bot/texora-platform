package com.texora.secops.cgi.execution.service;

import com.texora.secops.cgi.config.props.ExecutionProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/** Caps concurrent workers so a hung handler can never exhaust the gateway (CGI_TDD gateway failure handling). */
@Component
public class ExecutionBulkhead {

    private final Semaphore permits;

    public ExecutionBulkhead(ExecutionProperties properties) {
        this.permits = new Semaphore(Math.max(properties.getMaxConcurrentWorkers(), 1));
    }

    public boolean tryAcquire() {
        return permits.tryAcquire();
    }

    public void release() {
        permits.release();
    }

    public int availablePermits() {
        return permits.availablePermits();
    }
}
