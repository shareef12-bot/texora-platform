package com.texora.secops.sso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/** Maps a DC role/group to an application-specific scope/claim. */
@Entity
@Table(name = "sso_app_role_mapping", schema = "sso")
public class SsoAppRoleMapping {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "dc_role_or_group_id", nullable = false)
    private UUID dcRoleOrGroupId;

    @Column(name = "mapped_scope", nullable = false, length = 128)
    private String mappedScope;

    protected SsoAppRoleMapping() {
        // required by JPA
    }

    public SsoAppRoleMapping(UUID id, UUID tenantId, UUID applicationId, UUID dcRoleOrGroupId,
                              String mappedScope) {
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.dcRoleOrGroupId = dcRoleOrGroupId;
        this.mappedScope = mappedScope;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getDcRoleOrGroupId() {
        return dcRoleOrGroupId;
    }

    public void setDcRoleOrGroupId(UUID dcRoleOrGroupId) {
        this.dcRoleOrGroupId = dcRoleOrGroupId;
    }

    public String getMappedScope() {
        return mappedScope;
    }

    public void setMappedScope(String mappedScope) {
        this.mappedScope = mappedScope;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SsoAppRoleMapping)) {
            return false;
        }
        SsoAppRoleMapping that = (SsoAppRoleMapping) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SsoAppRoleMapping{id=" + id + ", applicationId=" + applicationId
                + ", mappedScope='" + mappedScope + "'}";
    }
}
