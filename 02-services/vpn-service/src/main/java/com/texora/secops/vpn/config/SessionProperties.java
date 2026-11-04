package com.texora.secops.vpn.config;

public class SessionProperties {

    private int maxMinutes = 480;

    private int revalidationIntervalSeconds = 60;

    private int maxUnverifiedSeconds = 300;

    private int identityStalenessSeconds = 300;

    private int establishGraceSeconds = 30;

    private int claimLeaseSeconds = 30;

    private int batchSize = 50;

    private int workerThreads = 8;

    private int teardownRetrySeconds = 10;

    public SessionProperties() {
    }

    public int getMaxMinutes() {
        return maxMinutes;
    }

    public void setMaxMinutes(int maxMinutes) {
        this.maxMinutes = maxMinutes;
    }

    public int getRevalidationIntervalSeconds() {
        return revalidationIntervalSeconds;
    }

    public void setRevalidationIntervalSeconds(int revalidationIntervalSeconds) {
        this.revalidationIntervalSeconds = revalidationIntervalSeconds;
    }

    public int getMaxUnverifiedSeconds() {
        return maxUnverifiedSeconds;
    }

    public void setMaxUnverifiedSeconds(int maxUnverifiedSeconds) {
        this.maxUnverifiedSeconds = maxUnverifiedSeconds;
    }

    public int getIdentityStalenessSeconds() {
        return identityStalenessSeconds;
    }

    public void setIdentityStalenessSeconds(int identityStalenessSeconds) {
        this.identityStalenessSeconds = identityStalenessSeconds;
    }

    public int getEstablishGraceSeconds() {
        return establishGraceSeconds;
    }

    public void setEstablishGraceSeconds(int establishGraceSeconds) {
        this.establishGraceSeconds = establishGraceSeconds;
    }

    public int getClaimLeaseSeconds() {
        return claimLeaseSeconds;
    }

    public void setClaimLeaseSeconds(int claimLeaseSeconds) {
        this.claimLeaseSeconds = claimLeaseSeconds;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getTeardownRetrySeconds() {
        return teardownRetrySeconds;
    }

    public void setTeardownRetrySeconds(int teardownRetrySeconds) {
        this.teardownRetrySeconds = teardownRetrySeconds;
    }

}
