package com.texora.secops.siem.dto.ingestion;

import com.texora.secops.siem.domain.enums.Protocol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class IngestionSourceRequest {

    @NotBlank
    private String name;

    @NotNull
    private Protocol protocol;

    @NotBlank
    private String mtlsCertRef;

    private String productId;

    public IngestionSourceRequest() {
        // required for JSON deserialization
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

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
}
