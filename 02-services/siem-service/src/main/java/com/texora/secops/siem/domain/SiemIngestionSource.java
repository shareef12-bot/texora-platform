package com.texora.secops.siem.domain;

import com.texora.secops.siem.domain.enums.Protocol;
import com.texora.secops.siem.domain.enums.SourceStatus;
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
 * An external channel authorized to push audit events into SOC/SIEM. Registered sources
 * authenticate to {@code MultiProtocolIngestionAdapter} via mTLS, never SSO tokens (B.6.1
 * carve-out for machine-to-machine ingestion). tenant_id here is the reserved platform tenant
 * id for infrastructure/product-wide channels; see V1 migration header comment.
 */
@Entity
@Table(name = "siem_ingestion_source", schema = "siem")
public class SiemIngestionSource {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 16)
    private Protocol protocol;

    @Column(name = "mtls_cert_ref", nullable = false, length = 512)
    private String mtlsCertRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SourceStatus status;

    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SiemIngestionSource() {
        // required by JPA
    }

    public SiemIngestionSource(UUID id, UUID tenantId, String name, Protocol protocol,
                                String mtlsCertRef, SourceStatus status, String productId,
                                UUID registeredBy, Instant registeredAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.protocol = protocol;
        this.mtlsCertRef = mtlsCertRef;
        this.status = status;
        this.productId = productId;
        this.registeredBy = registeredBy;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getMtlsCertRef() {
        return mtlsCertRef;
    }

    public void setMtlsCertRef(String mtlsCertRef) {
        this.mtlsCertRef = mtlsCertRef;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public void setStatus(SourceStatus status) {
        this.status = status;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SiemIngestionSource)) {
            return false;
        }
        SiemIngestionSource that = (SiemIngestionSource) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SiemIngestionSource{id=" + id + ", name='" + name + "', protocol=" + protocol
                + ", status=" + status + "}";
    }
}
