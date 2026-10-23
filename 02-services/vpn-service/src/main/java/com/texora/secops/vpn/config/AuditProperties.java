package com.texora.secops.vpn.config;

public class AuditProperties {

    private String topic = "vpn.audit.events";

    private int publishTimeoutMs = 3000;

    private int batchSize = 100;

    private int publishIntervalMs = 2000;

    private int retryBackoffSeconds = 10;

    private int claimLeaseSeconds = 30;

    public AuditProperties() {
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getPublishTimeoutMs() {
        return publishTimeoutMs;
    }

    public void setPublishTimeoutMs(int publishTimeoutMs) {
        this.publishTimeoutMs = publishTimeoutMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getPublishIntervalMs() {
        return publishIntervalMs;
    }

    public void setPublishIntervalMs(int publishIntervalMs) {
        this.publishIntervalMs = publishIntervalMs;
    }

    public int getRetryBackoffSeconds() {
        return retryBackoffSeconds;
    }

    public void setRetryBackoffSeconds(int retryBackoffSeconds) {
        this.retryBackoffSeconds = retryBackoffSeconds;
    }

    public int getClaimLeaseSeconds() {
        return claimLeaseSeconds;
    }

    public void setClaimLeaseSeconds(int claimLeaseSeconds) {
        this.claimLeaseSeconds = claimLeaseSeconds;
    }

}
