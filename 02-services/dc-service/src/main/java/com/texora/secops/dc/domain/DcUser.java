package com.texora.secops.dc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dc_user", schema = "dc")
public class DcUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "username", nullable = false, length = 128)
    private String username;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "org_unit_id", nullable = true, length = 128)
    private String orgUnitId;

    @Column(name = "hrms_employee_id", nullable = false, length = 128)
    private String hrmsEmployeeId;

    @Column(name = "hrms_last_modified", nullable = true)
    private Instant hrmsLastModified;

    @Column(name = "security_locked", nullable = false)
    private boolean securityLocked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DcUser() {
        // required by JPA / Jackson
    }

    public DcUser(UUID id, UUID tenantId, String username, String status, String displayName, String orgUnitId, String hrmsEmployeeId, Instant hrmsLastModified, boolean securityLocked, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.status = status;
        this.displayName = displayName;
        this.orgUnitId = orgUnitId;
        this.hrmsEmployeeId = hrmsEmployeeId;
        this.hrmsLastModified = hrmsLastModified;
        this.securityLocked = securityLocked;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(String orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getHrmsEmployeeId() {
        return hrmsEmployeeId;
    }

    public void setHrmsEmployeeId(String hrmsEmployeeId) {
        this.hrmsEmployeeId = hrmsEmployeeId;
    }

    public Instant getHrmsLastModified() {
        return hrmsLastModified;
    }

    public void setHrmsLastModified(Instant hrmsLastModified) {
        this.hrmsLastModified = hrmsLastModified;
    }

    public boolean isSecurityLocked() {
        return securityLocked;
    }

    public void setSecurityLocked(boolean securityLocked) {
        this.securityLocked = securityLocked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DcUser that = (DcUser) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DcUser{id=" + id + ", " + "username=" + username + ", " + "status=" + status + "}";
    }
}
