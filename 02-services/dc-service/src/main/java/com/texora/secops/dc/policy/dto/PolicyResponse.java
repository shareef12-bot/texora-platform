package com.texora.secops.dc.policy.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PolicyResponse {

    private UUID id;

    private String subjectType;

    private String resource;

    private String action;

    private String effect;

    private int version;

    private Instant createdAt;

    public PolicyResponse() {
        // required by Jackson
    }

    public PolicyResponse(UUID id, String subjectType, String resource, String action, String effect, int version, Instant createdAt) {
        this.id = id;
        this.subjectType = subjectType;
        this.resource = resource;
        this.action = action;
        this.effect = effect;
        this.version = version;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PolicyResponse that = (PolicyResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(subjectType, that.subjectType) && Objects.equals(resource, that.resource) && Objects.equals(action, that.action) && Objects.equals(effect, that.effect) && Objects.equals(version, that.version) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, subjectType, resource, action, effect, version, createdAt);
    }

    @Override
    public String toString() {
        return "PolicyResponse{id=" + id + ", " + "subjectType=" + subjectType + ", " + "resource=" + resource + ", " + "action=" + action + ", " + "effect=" + effect + ", " + "version=" + version + ", " + "createdAt=" + createdAt + "}";
    }
}
