package com.texora.secops.cgi.config.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant configuration entry as returned over HTTP.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class ConfigResponse {

    private String key;

    private String value;

    private UUID updatedBy;

    private Instant updatedAt;

    public ConfigResponse() {
        // default constructor (JSON / property binding)
    }

    public ConfigResponse(String key, String value, UUID updatedBy, Instant updatedAt) {
        this.key = key;
        this.value = value;
        this.updatedBy = updatedBy;
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigResponse)) {
            return false;
        }
        ConfigResponse that = (ConfigResponse) other;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value) && Objects.equals(updatedBy, that.updatedBy) && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, updatedBy, updatedAt);
    }

    @Override
    public String toString() {
        return "ConfigResponse{" + "key=" + key + ", value=" + value + ", updatedBy=" + updatedBy + ", updatedAt=" + updatedAt + "}";
    }

}
