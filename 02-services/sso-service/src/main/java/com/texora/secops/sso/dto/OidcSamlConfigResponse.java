package com.texora.secops.sso.dto;

import com.texora.secops.sso.domain.Protocol;

import java.util.UUID;

public class OidcSamlConfigResponse {

    private UUID id;
    private UUID applicationId;
    private Protocol protocol;
    private String redirectUris;
    private String signingCertRef;

    public OidcSamlConfigResponse() {
    }

    public OidcSamlConfigResponse(UUID id, UUID applicationId, Protocol protocol, String redirectUris,
                                   String signingCertRef) {
        this.id = id;
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
}
