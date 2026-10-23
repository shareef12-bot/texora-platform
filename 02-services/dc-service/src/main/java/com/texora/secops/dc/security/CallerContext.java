package com.texora.secops.dc.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CallerContext {

    private UUID tenantId;

    private UUID subjectId;

    private String email;

    private Set<String> roles;

    public CallerContext() {
        // required by Jackson
    }

    public CallerContext(UUID tenantId, UUID subjectId, String email, Set<String> roles) {
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.email = email;
        this.roles = roles;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        CallerContext that = (CallerContext) other;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(subjectId, that.subjectId) && Objects.equals(email, that.email) && Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, subjectId, email, roles);
    }

    @Override
    public String toString() {
        return "CallerContext{tenantId=" + tenantId + ", " + "subjectId=" + subjectId + "}";
    }
}
