package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class ConfigEntryResponse {

    private String key;

    private String value;

    private UUID updatedBy;

    private Instant updatedAt;

    public ConfigEntryResponse() {
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

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ConfigEntryResponse{" + "key=" + key + ", value=" + value + ", updatedBy=" + updatedBy + ", updatedAt=" + updatedAt + "}";
    }

}
