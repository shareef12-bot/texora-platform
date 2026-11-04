package com.texora.secops.dc.adapter;

import java.time.Instant;
import java.util.Objects;

public class HrmsEmployee {

    private String hrmsEmployeeId;

    private String username;

    private String displayName;

    private String orgUnitId;

    private String status;

    private Instant lastModified;

    public HrmsEmployee() {
        // required by Jackson
    }

    public HrmsEmployee(String hrmsEmployeeId, String username, String displayName, String orgUnitId, String status, Instant lastModified) {
        this.hrmsEmployeeId = hrmsEmployeeId;
        this.username = username;
        this.displayName = displayName;
        this.orgUnitId = orgUnitId;
        this.status = status;
        this.lastModified = lastModified;
    }

    public String getHrmsEmployeeId() {
        return hrmsEmployeeId;
    }

    public void setHrmsEmployeeId(String hrmsEmployeeId) {
        this.hrmsEmployeeId = hrmsEmployeeId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        HrmsEmployee that = (HrmsEmployee) other;
        return Objects.equals(hrmsEmployeeId, that.hrmsEmployeeId) && Objects.equals(username, that.username) && Objects.equals(displayName, that.displayName) && Objects.equals(orgUnitId, that.orgUnitId) && Objects.equals(status, that.status) && Objects.equals(lastModified, that.lastModified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hrmsEmployeeId, username, displayName, orgUnitId, status, lastModified);
    }

    @Override
    public String toString() {
        return "HrmsEmployee{hrmsEmployeeId=" + hrmsEmployeeId + ", " + "status=" + status + "}";
    }
}
