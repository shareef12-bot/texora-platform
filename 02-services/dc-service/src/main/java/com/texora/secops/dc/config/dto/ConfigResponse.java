package com.texora.secops.dc.config.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ConfigResponse {

    private UUID id;

    private String key;

    private String value;

    private String status;

    private UUID submittedBy;

    private Instant submittedAt;

    private Instant updatedAt;

    private String rejectionReason;

    private Instant activatedAt;

    public ConfigResponse() {
        // required by Jackson
    }

    public ConfigResponse(UUID id, String key, String value, String status, UUID submittedBy, Instant submittedAt, Instant updatedAt, String rejectionReason, Instant activatedAt) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.status = status;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.updatedAt = updatedAt;
        this.rejectionReason = rejectionReason;
        this.activatedAt = activatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ConfigResponse that = (ConfigResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(key, that.key) && Objects.equals(value, that.value) && Objects.equals(status, that.status) && Objects.equals(submittedBy, that.submittedBy) && Objects.equals(submittedAt, that.submittedAt) && Objects.equals(updatedAt, that.updatedAt) && Objects.equals(rejectionReason, that.rejectionReason) && Objects.equals(activatedAt, that.activatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, value, status, submittedBy, submittedAt, updatedAt, rejectionReason, activatedAt);
    }

    @Override
    public String toString() {
        return "ConfigResponse{id=" + id + ", " + "key=" + key + ", " + "status=" + status + "}";
    }
}
