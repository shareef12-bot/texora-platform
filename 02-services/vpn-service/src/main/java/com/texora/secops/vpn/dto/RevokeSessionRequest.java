package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RevokeSessionRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;

    public RevokeSessionRequest() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "RevokeSessionRequest{" + "reason=" + reason + "}";
    }

}
