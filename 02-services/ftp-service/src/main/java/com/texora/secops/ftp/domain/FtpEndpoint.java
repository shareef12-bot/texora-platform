package com.texora.secops.ftp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ftp_endpoint", schema = "ftp")
public class FtpEndpoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "host", length = 253, nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 16, nullable = false)
    private EndpointDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", length = 16, nullable = false)
    private TransferProtocol protocol;

    @Column(name = "base_path", length = 1024, nullable = false)
    private String basePath;

    @Column(name = "credential_ref", length = 255, nullable = false)
    private String credentialRef;

    @Column(name = "host_key_fingerprint", length = 255)
    private String hostKeyFingerprint;

    @Column(name = "allowlisted", nullable = false)
    private boolean allowlisted;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected FtpEndpoint() { // required by JPA
    }

    public FtpEndpoint(UUID id, UUID tenantId, String host, int port, EndpointDirection direction, TransferProtocol protocol, String basePath, String credentialRef, String hostKeyFingerprint, boolean allowlisted, UUID addedBy, Instant addedAt) {
        this.id = id;
        this.tenantId = tenantId;
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

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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

    public EndpointDirection getDirection() {
        return direction;
    }

    public void setDirection(EndpointDirection direction) {
        this.direction = direction;
    }

    public TransferProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(TransferProtocol protocol) {
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
        if (!(other instanceof FtpEndpoint)) {
            return false;
        }
        FtpEndpoint that = (FtpEndpoint) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FtpEndpoint{" + "id=" + id + ", tenantId=" + tenantId + ", host=" + host + ", port=" + port + ", direction=" + direction + ", protocol=" + protocol + ", allowlisted=" + allowlisted + "}";
    }
}
