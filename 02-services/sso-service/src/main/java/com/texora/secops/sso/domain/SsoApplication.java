package com.texora.secops.sso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered relying-party / service-provider application (SSO-F-007).
 * One row per product (ilmora, texora-jobs, taskorbit, hrms, crm) per ADR D-3.
 */
@Entity
@Table(name = "sso_application", schema = "sso")
public class SsoApplication {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false, unique = true, length = 128)
    private String clientId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApplicationStatus status;

    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected SsoApplication() {
        // required by JPA
    }

    public SsoApplication(UUID id, UUID tenantId, String clientId, String name, String productId,
                           ApplicationStatus status, UUID registeredBy, Instant registeredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.name = name;
        this.productId = productId;
        this.status = status;
        this.registeredBy = registeredBy;
        this.registeredAt = registeredAt;
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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public UUID getRegisteredBy() {
        return registeredBy;
    }

    public void setRegisteredBy(UUID registeredBy) {
        this.registeredBy = registeredBy;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SsoApplication)) {
            return false;
        }
        SsoApplication that = (SsoApplication) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SsoApplication{id=" + id + ", clientId='" + clientId + "', productId='" + productId
                + "', status=" + status + "}";
    }
}
