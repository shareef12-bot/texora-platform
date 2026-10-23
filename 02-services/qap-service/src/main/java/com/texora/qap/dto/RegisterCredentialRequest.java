package com.texora.qap.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterCredentialRequest {

    @NotBlank
    private String productId;

    @NotBlank
    private String secSecretReferenceId;

    private String description;

    public RegisterCredentialRequest() {}

    public RegisterCredentialRequest(String productId, String secSecretReferenceId, String description) {
        this.productId = productId;
        this.secSecretReferenceId = secSecretReferenceId;
        this.description = description;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSecSecretReferenceId() { return secSecretReferenceId; }
    public void setSecSecretReferenceId(String secSecretReferenceId) { this.secSecretReferenceId = secSecretReferenceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
