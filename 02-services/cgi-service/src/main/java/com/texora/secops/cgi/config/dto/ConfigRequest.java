package com.texora.secops.cgi.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * POST /api/v1/config request body.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class ConfigRequest {

    @NotBlank
    @Size(max = 100)
    private String key;

    @NotNull
    @Size(max = 1000)
    private String value;

    public ConfigRequest() {
        // default constructor (JSON / property binding)
    }

    public ConfigRequest(String key, String value) {
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
        if (!(other instanceof ConfigRequest)) {
            return false;
        }
        ConfigRequest that = (ConfigRequest) other;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "ConfigRequest{" + "key=" + key + ", value=" + value + "}";
    }

}
