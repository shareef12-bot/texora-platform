package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.Objects;

public class ConfigEntryResponse {

    private String key;

    private String value;

    private boolean defaulted;

    private Instant updatedAt;

    public ConfigEntryResponse() {
    }

    public ConfigEntryResponse(String key, String value, boolean defaulted, Instant updatedAt) {
        this.key = key;
        this.value = value;
        this.defaulted = defaulted;
        this.updatedAt = updatedAt;
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

    public boolean isDefaulted() {
        return defaulted;
    }

    public void setDefaulted(boolean defaulted) {
        this.defaulted = defaulted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigEntryResponse)) {
            return false;
        }
        ConfigEntryResponse that = (ConfigEntryResponse) other;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value) && Objects.equals(defaulted, that.defaulted) && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, defaulted, updatedAt);
    }

    @Override
    public String toString() {
        return "ConfigEntryResponse{" + "key=" + key + ", value=" + value + ", defaulted=" + defaulted + ", updatedAt=" + updatedAt + "}";
    }
}
