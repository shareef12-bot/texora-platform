package com.texora.secops.siem.dto.config;

import com.texora.secops.siem.domain.SiemConfigItem;

import java.time.Instant;
import java.util.UUID;

public class ConfigItemResponse {

    private UUID id;
    private String key;
    private String value;
    private Instant updatedAt;

    public ConfigItemResponse() {
        // required for JSON serialization
    }

    public static ConfigItemResponse from(SiemConfigItem item) {
        ConfigItemResponse response = new ConfigItemResponse();
        response.setId(item.getId());
        response.setKey(item.getConfigKey());
        response.setValue(item.getConfigValue());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
