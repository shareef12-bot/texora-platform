package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * mfaCode is excluded from toString on purpose; it must never be logged.
 */
public class EstablishSessionRequest {

    @NotNull
    private UUID accessPolicyId;

    @NotBlank
    @Size(max = 255)
    private String deviceFingerprint;

    @NotBlank
    @Size(max = 32)
    private String mfaCode;

    public EstablishSessionRequest() {
    }

    public UUID getAccessPolicyId() {
        return accessPolicyId;
    }

    public void setAccessPolicyId(UUID accessPolicyId) {
        this.accessPolicyId = accessPolicyId;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getMfaCode() {
        return mfaCode;
    }

    public void setMfaCode(String mfaCode) {
        this.mfaCode = mfaCode;
    }

    @Override
    public String toString() {
        return "EstablishSessionRequest{" + "accessPolicyId=" + accessPolicyId + ", deviceFingerprint=" + deviceFingerprint + "}";
    }

}
