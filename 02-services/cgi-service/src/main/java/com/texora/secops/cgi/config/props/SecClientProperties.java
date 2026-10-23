package com.texora.secops.cgi.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cgi.sec.* settings (Security Server client).
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@ConfigurationProperties(prefix = "cgi.sec")
public class SecClientProperties {

    private String baseUrl = "http://localhost:8083";

    private int connectTimeoutMs = 2000;

    private int readTimeoutMs = 3000;

    private String sslBundle = "";

    public SecClientProperties() {
        // default constructor (JSON / property binding)
    }

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

    public String getSslBundle() {
        return sslBundle;
    }

    public void setSslBundle(String sslBundle) {
        this.sslBundle = sslBundle;
    }

}
