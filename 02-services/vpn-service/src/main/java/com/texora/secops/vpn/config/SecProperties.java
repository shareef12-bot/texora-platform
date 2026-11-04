package com.texora.secops.vpn.config;

/**
 * SEC client settings. timeoutMs is the per-attempt SEC timeout: exceeding it means UNCERTAIN.
 */
public class SecProperties {

    private String baseUrl = "https://sec-service.secops.svc:8443";

    private String sslBundle = "";

    private int timeoutMs = 2000;

    private int revalidationAttempts = 2;

    private int revalidationDeadlineMs = 5000;

    public SecProperties() {
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSslBundle() {
        return sslBundle;
    }

    public void setSslBundle(String sslBundle) {
        this.sslBundle = sslBundle;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getRevalidationAttempts() {
        return revalidationAttempts;
    }

    public void setRevalidationAttempts(int revalidationAttempts) {
        this.revalidationAttempts = revalidationAttempts;
    }

    public int getRevalidationDeadlineMs() {
        return revalidationDeadlineMs;
    }

    public void setRevalidationDeadlineMs(int revalidationDeadlineMs) {
        this.revalidationDeadlineMs = revalidationDeadlineMs;
    }

}
