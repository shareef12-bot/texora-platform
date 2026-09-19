package com.texora.secops.sso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Per-application federation settings. signing_cert_ref points to SEC/KMS —
 * raw key material is NEVER stored in this row or anywhere in the sso schema.
 */
@Entity
@Table(name = "sso_oidc_saml_config", schema = "sso")
public class SsoOidcSamlConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 16)
    private Protocol protocol;

    @Column(name = "redirect_uris", nullable = false, columnDefinition = "text")
    private String redirectUris;

    @Column(name = "signing_cert_ref", nullable = false, length = 512)
    private String signingCertRef;

    protected SsoOidcSamlConfig() {
        // required by JPA
    }

    public SsoOidcSamlConfig(UUID id, UUID tenantId, UUID applicationId, Protocol protocol,
                              String redirectUris, String signingCertRef) {
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.protocol = protocol;
        this.redirectUris = redirectUris;
        this.signingCertRef = signingCertRef;
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

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getSigningCertRef() {
        return signingCertRef;
    }

    public void setSigningCertRef(String signingCertRef) {
        this.signingCertRef = signingCertRef;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SsoOidcSamlConfig)) {
            return false;
        }
        SsoOidcSamlConfig that = (SsoOidcSamlConfig) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SsoOidcSamlConfig{id=" + id + ", applicationId=" + applicationId
                + ", protocol=" + protocol + "}";
    }
}
