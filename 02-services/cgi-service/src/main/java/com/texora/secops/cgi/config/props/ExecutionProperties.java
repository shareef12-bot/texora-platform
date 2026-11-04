package com.texora.secops.cgi.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cgi.execution.* settings.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@ConfigurationProperties(prefix = "cgi.execution")
public class ExecutionProperties {

    private String adapter = "stub";

    private int maxConcurrentWorkers = 16;

    private int maxTimeoutCeilingSeconds = 120;

    private long maxRequestBodyBytes = 1_048_576L;

    private int maxResponseBytes = 2_097_152;

    private String defaultSandboxProfile = "strict-default";

    public ExecutionProperties() {
        // default constructor (JSON / property binding)
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public int getMaxConcurrentWorkers() {
        return maxConcurrentWorkers;
    }

    public void setMaxConcurrentWorkers(int maxConcurrentWorkers) {
        this.maxConcurrentWorkers = maxConcurrentWorkers;
    }

    public int getMaxTimeoutCeilingSeconds() {
        return maxTimeoutCeilingSeconds;
    }

    public void setMaxTimeoutCeilingSeconds(int maxTimeoutCeilingSeconds) {
        this.maxTimeoutCeilingSeconds = maxTimeoutCeilingSeconds;
    }

    public long getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(long maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public String getDefaultSandboxProfile() {
        return defaultSandboxProfile;
    }

    public void setDefaultSandboxProfile(String defaultSandboxProfile) {
        this.defaultSandboxProfile = defaultSandboxProfile;
    }

}
