package com.texora.secops.ftp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public class ConfigEntryRequest {

    @NotBlank
    @Size(max = 128)
    private String key;

    @NotBlank
    @Size(max = 512)
    private String value;

    public ConfigEntryRequest() {
    }

    public ConfigEntryRequest(String key, String value) {
        this.key = key;
        this.value = value;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigEntryRequest)) {
            return false;
        }
        ConfigEntryRequest that = (ConfigEntryRequest) other;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "ConfigEntryRequest{" + "key=" + key + ", value=" + value + "}";
    }
}
