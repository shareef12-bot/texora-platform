package com.texora.secops.ftp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Non-secret configuration only. Secrets come from SEC at runtime (Shared Standards B.6 #6). */
@ConfigurationProperties(prefix = "ftp")
public class FtpProperties {

    private final Sec sec = new Sec();
    private final Inspection inspection = new Inspection();
    private final Staging staging = new Staging();
    private final Transfer transfer = new Transfer();

    public Sec getSec() {
        return sec;
    }

    public Inspection getInspection() {
        return inspection;
    }

    public Staging getStaging() {
        return staging;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public static class Sec {
        private String baseUrl = "http://localhost:8082";
        private int timeoutMs = 3000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Inspection {
        private String baseUrl = "";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 60000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Staging {
        private String directory = "/var/lib/ftp-staging";

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }
    }

    public static class Transfer {
        private int connectTimeoutMs = 15000;
        private int ioTimeoutMs = 60000;
        private long retryBackoffMs = 2000;
        private int defaultMaxAttempts = 3;
        private long defaultMaxFileBytes = 5368709120L;
        private boolean allowLoopbackTargets = false;
        private int executorCoreSize = 4;
        private int executorMaxSize = 8;
        private int executorQueueCapacity = 100;

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getIoTimeoutMs() {
            return ioTimeoutMs;
        }

        public void setIoTimeoutMs(int ioTimeoutMs) {
            this.ioTimeoutMs = ioTimeoutMs;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public int getDefaultMaxAttempts() {
            return defaultMaxAttempts;
        }

        public void setDefaultMaxAttempts(int defaultMaxAttempts) {
            this.defaultMaxAttempts = defaultMaxAttempts;
        }

        public long getDefaultMaxFileBytes() {
            return defaultMaxFileBytes;
        }

        public void setDefaultMaxFileBytes(long defaultMaxFileBytes) {
            this.defaultMaxFileBytes = defaultMaxFileBytes;
        }

        public boolean isAllowLoopbackTargets() {
            return allowLoopbackTargets;
        }

        public void setAllowLoopbackTargets(boolean allowLoopbackTargets) {
            this.allowLoopbackTargets = allowLoopbackTargets;
        }

        public int getExecutorCoreSize() {
            return executorCoreSize;
        }

        public void setExecutorCoreSize(int executorCoreSize) {
            this.executorCoreSize = executorCoreSize;
        }

        public int getExecutorMaxSize() {
            return executorMaxSize;
        }

        public void setExecutorMaxSize(int executorMaxSize) {
            this.executorMaxSize = executorMaxSize;
        }

        public int getExecutorQueueCapacity() {
            return executorQueueCapacity;
        }

        public void setExecutorQueueCapacity(int executorQueueCapacity) {
            this.executorQueueCapacity = executorQueueCapacity;
        }
    }
}
