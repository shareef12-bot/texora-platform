package com.texora.secops.ftp.dto;

import java.util.Objects;

public class PolicySummary {

    private String id;

    private String name;

    private String description;

    public PolicySummary() {
    }

    public PolicySummary(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PolicySummary)) {
            return false;
        }
        PolicySummary that = (PolicySummary) other;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description);
    }

    @Override
    public String toString() {
        return "PolicySummary{" + "id=" + id + ", name=" + name + ", description=" + description + "}";
    }
}
