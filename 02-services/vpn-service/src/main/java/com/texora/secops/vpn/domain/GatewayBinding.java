package com.texora.secops.vpn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * dataplaneSessionRef is an OPAQUE reference. No credential material is stored anywhere in this table.
 */
@Entity
@Table(name = "vpn_gateway_binding", schema = "vpn")
public class GatewayBinding {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "dataplane_session_ref", nullable = true, length = 255)
    private String dataplaneSessionRef;

    @Column(name = "gateway_endpoint", nullable = false, length = 512)
    private String gatewayEndpoint;

    @Column(name = "bound_at", nullable = false)
    private Instant boundAt;

    @Column(name = "teardown_state", nullable = false, length = 16)
    private String teardownState;

    @Column(name = "teardown_attempts", nullable = false)
    private int teardownAttempts;

    @Column(name = "last_teardown_attempt_at", nullable = true)
    private Instant lastTeardownAttemptAt;

    public GatewayBinding() {
        // required by JPA
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

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getDataplaneSessionRef() {
        return dataplaneSessionRef;
    }

    public void setDataplaneSessionRef(String dataplaneSessionRef) {
        this.dataplaneSessionRef = dataplaneSessionRef;
    }

    public String getGatewayEndpoint() {
        return gatewayEndpoint;
    }

    public void setGatewayEndpoint(String gatewayEndpoint) {
        this.gatewayEndpoint = gatewayEndpoint;
    }

    public Instant getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(Instant boundAt) {
        this.boundAt = boundAt;
    }

    public String getTeardownState() {
        return teardownState;
    }

    public void setTeardownState(String teardownState) {
        this.teardownState = teardownState;
    }

    public int getTeardownAttempts() {
        return teardownAttempts;
    }

    public void setTeardownAttempts(int teardownAttempts) {
        this.teardownAttempts = teardownAttempts;
    }

    public Instant getLastTeardownAttemptAt() {
        return lastTeardownAttemptAt;
    }

    public void setLastTeardownAttemptAt(Instant lastTeardownAttemptAt) {
        this.lastTeardownAttemptAt = lastTeardownAttemptAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayBinding)) {
            return false;
        }
        GatewayBinding that = (GatewayBinding) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GatewayBinding{" + "id=" + id + ", tenantId=" + tenantId + "}";
    }

}
