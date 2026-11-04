package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DeviceEnrollmentRequest {

    @NotBlank
    @Size(max = 255)
    private String deviceFingerprint;

    @NotBlank
    @Size(max = 255)
    private String mfaFactorRef;

    public DeviceEnrollmentRequest() {
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getMfaFactorRef() {
        return mfaFactorRef;
    }

    public void setMfaFactorRef(String mfaFactorRef) {
        this.mfaFactorRef = mfaFactorRef;
    }

    @Override
    public String toString() {
        return "DeviceEnrollmentRequest{" + "deviceFingerprint=" + deviceFingerprint + ", mfaFactorRef=" + mfaFactorRef + "}";
    }

}
