package com.texora.secops.siem.dto.policy;

public class PolicyResponse {

    private String id;
    private String name;
    private String description;
    private String scope;

    public PolicyResponse() {
        // required for JSON deserialization
    }

    public PolicyResponse(String id, String name, String description, String scope) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.scope = scope;
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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
