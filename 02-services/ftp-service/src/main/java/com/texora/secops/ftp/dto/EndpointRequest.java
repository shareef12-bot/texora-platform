package com.texora.secops.ftp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * credentialRef is an opaque SEC reference. Never put a password or key here.
 */
public class EndpointRequest {

    @NotBlank
    @Size(max = 253)
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;

    @NotNull
    @Pattern(regexp = "^(SOURCE|DEST)$")
    private String direction;

    @NotNull
    @Pattern(regexp = "^(SFTP|FTPS)$")
    private String protocol;

    @Size(max = 1024)
    private String basePath;

    @NotBlank
    @Size(max = 255)
    private String credentialRef;

    @Size(max = 255)
    private String hostKeyFingerprint;

    private Boolean allowlisted;

    public EndpointRequest() {
    }

    public EndpointRequest(String host, Integer port, String direction, String protocol, String basePath, String credentialRef, String hostKeyFingerprint, Boolean allowlisted) {
        this.host = host;
        this.port = port;
        this.direction = direction;
        this.protocol = protocol;
        this.basePath = basePath;
        this.credentialRef = credentialRef;
        this.hostKeyFingerprint = hostKeyFingerprint;
        this.allowlisted = allowlisted;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getHostKeyFingerprint() {
        return hostKeyFingerprint;
    }

    public void setHostKeyFingerprint(String hostKeyFingerprint) {
        this.hostKeyFingerprint = hostKeyFingerprint;
    }

    public Boolean getAllowlisted() {
        return allowlisted;
    }

    public void setAllowlisted(Boolean allowlisted) {
        this.allowlisted = allowlisted;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EndpointRequest)) {
            return false;
        }
        EndpointRequest that = (EndpointRequest) other;
        return Objects.equals(host, that.host) && Objects.equals(port, that.port) && Objects.equals(direction, that.direction) && Objects.equals(protocol, that.protocol) && Objects.equals(basePath, that.basePath) && Objects.equals(credentialRef, that.credentialRef) && Objects.equals(hostKeyFingerprint, that.hostKeyFingerprint) && Objects.equals(allowlisted, that.allowlisted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, direction, protocol, basePath, credentialRef, hostKeyFingerprint, allowlisted);
    }

    @Override
    public String toString() {
        return "EndpointRequest{" + "host=" + host + ", port=" + port + ", direction=" + direction + ", protocol=" + protocol + ", basePath=" + basePath + ", credentialRef=" + credentialRef + ", hostKeyFingerprint=" + hostKeyFingerprint + ", allowlisted=" + allowlisted + "}";
    }
}
