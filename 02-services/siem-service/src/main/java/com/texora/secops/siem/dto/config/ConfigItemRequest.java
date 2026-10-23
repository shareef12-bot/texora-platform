package com.texora.secops.siem.dto.config;

import jakarta.validation.constraints.NotBlank;

public class ConfigItemRequest {

    @NotBlank
    private String key;

    @NotBlank
    private String value;

    public ConfigItemRequest() {
        // required for JSON deserialization
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
}
