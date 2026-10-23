package com.texora.secops.dc.admin.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class UserResponse {

    private UUID id;

    private String username;

    private String status;

    private String displayName;

    private String orgUnitId;

    private String hrmsEmployeeId;

    private boolean securityLocked;

    private String source;

    private Instant createdAt;

    private Instant updatedAt;

    public UserResponse() {
        // required by Jackson
    }

    public UserResponse(UUID id, String username, String status, String displayName, String orgUnitId, String hrmsEmployeeId, boolean securityLocked, String source, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.status = status;
        this.displayName = displayName;
        this.orgUnitId = orgUnitId;
        this.hrmsEmployeeId = hrmsEmployeeId;
        this.securityLocked = securityLocked;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public boolean isSecurityLocked() {
        return securityLocked;
    }

    public void setSecurityLocked(boolean securityLocked) {
        this.securityLocked = securityLocked;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
        UserResponse that = (UserResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(username, that.username) && Objects.equals(status, that.status) && Objects.equals(displayName, that.displayName) && Objects.equals(orgUnitId, that.orgUnitId) && Objects.equals(hrmsEmployeeId, that.hrmsEmployeeId) && Objects.equals(securityLocked, that.securityLocked) && Objects.equals(source, that.source) && Objects.equals(createdAt, that.createdAt) && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, status, displayName, orgUnitId, hrmsEmployeeId, securityLocked, source, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "UserResponse{id=" + id + ", " + "username=" + username + ", " + "status=" + status + "}";
    }
}
