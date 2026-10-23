package com.texora.secops.cgi.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cgi.audit.* settings.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@ConfigurationProperties(prefix = "cgi.audit")
public class AuditProperties {

    private String topic = "cgi.audit.events";

    private int sendTimeoutMs = 3000;

    private long retryIntervalMs = 30000L;

    private long retryMinAgeMs = 10000L;

    private int maxForwardAttempts = 1000;

    public AuditProperties() {
        // default constructor (JSON / property binding)
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(int sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public long getRetryMinAgeMs() {
        return retryMinAgeMs;
    }

    public void setRetryMinAgeMs(long retryMinAgeMs) {
        this.retryMinAgeMs = retryMinAgeMs;
    }

    public int getMaxForwardAttempts() {
        return maxForwardAttempts;
    }

    public void setMaxForwardAttempts(int maxForwardAttempts) {
        this.maxForwardAttempts = maxForwardAttempts;
    }

}
