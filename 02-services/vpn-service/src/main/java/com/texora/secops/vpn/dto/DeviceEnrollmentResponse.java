package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class DeviceEnrollmentResponse {

    private UUID id;

    private UUID subjectId;

    private String deviceFingerprint;

    private String mfaFactorRef;

    private String status;

    private Instant enrolledAt;

    public DeviceEnrollmentResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    @Override
    public String toString() {
        return "DeviceEnrollmentResponse{" + "id=" + id + ", subjectId=" + subjectId + ", deviceFingerprint=" + deviceFingerprint + ", mfaFactorRef=" + mfaFactorRef + ", status=" + status + ", enrolledAt=" + enrolledAt + "}";
    }

}
