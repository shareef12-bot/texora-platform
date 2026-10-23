package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class EndpointResponse {

    private UUID id;

    private String host;

    private int port;

    private String direction;

    private String protocol;

    private String basePath;

    private String credentialRef;

    private String hostKeyFingerprint;

    private boolean allowlisted;

    private UUID addedBy;

    private Instant addedAt;

    public EndpointResponse() {
    }

    public EndpointResponse(UUID id, String host, int port, String direction, String protocol, String basePath, String credentialRef, String hostKeyFingerprint, boolean allowlisted, UUID addedBy, Instant addedAt) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.direction = direction;
        this.protocol = protocol;
        this.basePath = basePath;
        this.credentialRef = credentialRef;
        this.hostKeyFingerprint = hostKeyFingerprint;
        this.allowlisted = allowlisted;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public boolean isAllowlisted() {
        return allowlisted;
    }

    public void setAllowlisted(boolean allowlisted) {
        this.allowlisted = allowlisted;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(UUID addedBy) {
        this.addedBy = addedBy;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EndpointResponse)) {
            return false;
        }
        EndpointResponse that = (EndpointResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(host, that.host) && Objects.equals(port, that.port) && Objects.equals(direction, that.direction) && Objects.equals(protocol, that.protocol) && Objects.equals(basePath, that.basePath) && Objects.equals(credentialRef, that.credentialRef) && Objects.equals(hostKeyFingerprint, that.hostKeyFingerprint) && Objects.equals(allowlisted, that.allowlisted) && Objects.equals(addedBy, that.addedBy) && Objects.equals(addedAt, that.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, host, port, direction, protocol, basePath, credentialRef, hostKeyFingerprint, allowlisted, addedBy, addedAt);
    }

    @Override
    public String toString() {
        return "EndpointResponse{" + "id=" + id + ", host=" + host + ", port=" + port + ", direction=" + direction + ", protocol=" + protocol + ", basePath=" + basePath + ", credentialRef=" + credentialRef + ", hostKeyFingerprint=" + hostKeyFingerprint + ", allowlisted=" + allowlisted + ", addedBy=" + addedBy + ", addedAt=" + addedAt + "}";
    }
}
