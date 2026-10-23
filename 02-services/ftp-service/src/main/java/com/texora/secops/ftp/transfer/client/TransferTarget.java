package com.texora.secops.ftp.transfer.client;

import java.util.Objects;

/**
 * Protocol-neutral description of a remote endpoint. No library types.
 */
public class TransferTarget {

    private String protocol;

    private String host;

    private int port;

    private String basePath;

    private String hostKeyFingerprint;

    public TransferTarget() {
    }

    public TransferTarget(String protocol, String host, int port, String basePath, String hostKeyFingerprint) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.basePath = basePath;
        this.hostKeyFingerprint = hostKeyFingerprint;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getHostKeyFingerprint() {
        return hostKeyFingerprint;
    }

    public void setHostKeyFingerprint(String hostKeyFingerprint) {
        this.hostKeyFingerprint = hostKeyFingerprint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransferTarget)) {
            return false;
        }
        TransferTarget that = (TransferTarget) other;
        return Objects.equals(protocol, that.protocol) && Objects.equals(host, that.host) && Objects.equals(port, that.port) && Objects.equals(basePath, that.basePath) && Objects.equals(hostKeyFingerprint, that.hostKeyFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, host, port, basePath, hostKeyFingerprint);
    }

    @Override
    public String toString() {
        return "TransferTarget{" + "protocol=" + protocol + ", host=" + host + ", port=" + port + ", basePath=" + basePath + ", hostKeyFingerprint=" + hostKeyFingerprint + "}";
    }
}
