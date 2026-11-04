package com.texora.secops.ldap.schema.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for submitting a new LDAP schema object-class definition.
 */
public class SchemaDefinitionRequest {

    private String objectClass;
    private List<AttributeRequest> attributes = new ArrayList<>();

    public SchemaDefinitionRequest() {
    }

    public SchemaDefinitionRequest(String objectClass, List<AttributeRequest> attributes) {
        this.objectClass = objectClass;
        this.attributes = attributes;
    }

    public String getObjectClass() {
        return objectClass;
    }

    public void setObjectClass(String objectClass) {
        this.objectClass = objectClass;
    }

    public List<AttributeRequest> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<AttributeRequest> attributes) {
        this.attributes = attributes;
    }
}
