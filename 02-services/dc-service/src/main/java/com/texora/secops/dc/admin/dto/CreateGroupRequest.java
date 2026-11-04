package com.texora.secops.dc.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = false)
public class CreateGroupRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String description;

    @Size(max = 128)
    private String orgUnitId;

    public CreateGroupRequest() {
        // required by Jackson
    }

    public CreateGroupRequest(String name, String description, String orgUnitId) {
        this.name = name;
        this.description = description;
        this.orgUnitId = orgUnitId;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        CreateGroupRequest that = (CreateGroupRequest) other;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(orgUnitId, that.orgUnitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, orgUnitId);
    }

    @Override
    public String toString() {
        return "CreateGroupRequest{name=" + name + ", " + "description=" + description + ", " + "orgUnitId=" + orgUnitId + "}";
    }
}
