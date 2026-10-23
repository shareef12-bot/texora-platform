package com.texora.secops.cgi.security.dto;

import java.util.Objects;

/**
 * One security policy applicable to gateway/route decisions.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class PolicyInfo {

    private String id;

    private String description;

    private String enforcement;

    public PolicyInfo() {
        // default constructor (JSON / property binding)
    }

    public PolicyInfo(String id, String description, String enforcement) {
        this.id = id;
        this.description = description;
        this.enforcement = enforcement;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEnforcement() {
        return enforcement;
    }

    public void setEnforcement(String enforcement) {
        this.enforcement = enforcement;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PolicyInfo)) {
            return false;
        }
        PolicyInfo that = (PolicyInfo) other;
        return Objects.equals(id, that.id) && Objects.equals(description, that.description) && Objects.equals(enforcement, that.enforcement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, enforcement);
    }

    @Override
    public String toString() {
        return "PolicyInfo{" + "id=" + id + ", description=" + description + ", enforcement=" + enforcement + "}";
    }

}
