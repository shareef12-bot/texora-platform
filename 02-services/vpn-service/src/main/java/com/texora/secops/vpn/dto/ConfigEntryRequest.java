package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ConfigEntryRequest {

    @NotBlank
    @Size(max = 128)
    private String key;

    @NotBlank
    @Size(max = 512)
    private String value;

    public ConfigEntryRequest() {
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
    public String toString() {
        return "ConfigEntryRequest{" + "key=" + key + ", value=" + value + "}";
    }

}
