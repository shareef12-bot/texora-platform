package com.texora.secops.sso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Request body for POST /api/v1/application-registration. */
public class ApplicationRegistrationRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String productId;

    @NotNull
    private com.texora.secops.sso.domain.Protocol protocol;

    @NotEmpty
    private List<String> redirectUris;

    public ApplicationRegistrationRequest() {
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

    public com.texora.secops.sso.domain.Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(com.texora.secops.sso.domain.Protocol protocol) {
        this.protocol = protocol;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }
}
