package com.texora.secops.sso.dto;

import com.texora.secops.sso.domain.Protocol;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class OidcSamlConfigRequest {

    @NotNull
    private UUID applicationId;

    @NotNull
    private Protocol protocol;

    @NotEmpty
    private List<String> redirectUris;

    public OidcSamlConfigRequest() {
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

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }
}
