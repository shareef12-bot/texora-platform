package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public class AccessPolicyRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private Map<String, Object> conditions;

    private UUID replacesPolicyId;

    public AccessPolicyRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    public void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions;
    }

    public UUID getReplacesPolicyId() {
        return replacesPolicyId;
    }

    public void setReplacesPolicyId(UUID replacesPolicyId) {
        this.replacesPolicyId = replacesPolicyId;
    }

    @Override
    public String toString() {
        return "AccessPolicyRequest{" + "name=" + name + ", conditions=" + conditions + ", replacesPolicyId=" + replacesPolicyId + "}";
    }

}
