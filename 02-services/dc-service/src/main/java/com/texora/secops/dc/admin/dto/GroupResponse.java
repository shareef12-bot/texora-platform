package com.texora.secops.dc.admin.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class GroupResponse {

    private UUID id;

    private String name;

    private String description;

    private String orgUnitId;

    private Instant createdAt;

    private Instant updatedAt;

    public GroupResponse() {
        // required by Jackson
    }

    public GroupResponse(UUID id, String name, String description, String orgUnitId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.orgUnitId = orgUnitId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(String orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        GroupResponse that = (GroupResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(orgUnitId, that.orgUnitId) && Objects.equals(createdAt, that.createdAt) && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, orgUnitId, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "GroupResponse{id=" + id + ", " + "name=" + name + "}";
    }
}
