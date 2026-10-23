package com.texora.secops.vpn.dto;

import java.util.Map;
import java.util.UUID;

public class SecurityPolicyView {

    private UUID id;

    private String name;

    private String effect;

    private Map<String, Object> conditions;

    public SecurityPolicyView() {
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

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    public void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions;
    }

    @Override
    public String toString() {
        return "SecurityPolicyView{" + "id=" + id + ", name=" + name + ", effect=" + effect + ", conditions=" + conditions + "}";
    }

}
