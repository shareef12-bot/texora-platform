package com.texora.secops.dc.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ConfigSubmitRequest {

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String key;

    @NotNull
    @Size(max = 4000)
    private String value;

    public ConfigSubmitRequest() {
        // required by Jackson
    }

    public ConfigSubmitRequest(String key, String value) {
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
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ConfigSubmitRequest that = (ConfigSubmitRequest) other;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "ConfigSubmitRequest{key=" + key + "}";
    }
}
