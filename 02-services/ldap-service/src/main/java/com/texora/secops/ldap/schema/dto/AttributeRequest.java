package com.texora.secops.ldap.schema.dto;

/**
 * Attribute definition within a schema submission request.
 */
public class AttributeRequest {

    private String name;
    private String syntax;
    private boolean required;

    public AttributeRequest() {
    }

    public AttributeRequest(String name, String syntax, boolean required) {
        this.name = name;
        this.syntax = syntax;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSyntax() {
        return syntax;
    }

    public void setSyntax(String syntax) {
        this.syntax = syntax;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
