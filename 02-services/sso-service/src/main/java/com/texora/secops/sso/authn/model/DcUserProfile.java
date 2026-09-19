package com.texora.secops.sso.authn.model;

import java.util.List;
import java.util.UUID;

/** User/group/role data resolved from DC (or HRMS per decision D-2), used to populate token claims. */
public final class DcUserProfile {

    private final UUID dcUserId;
    private final String username;
    private final List<UUID> groupIds;
    private final List<String> roles;

    public DcUserProfile(UUID dcUserId, String username, List<UUID> groupIds, List<String> roles) {
        this.dcUserId = dcUserId;
        this.username = username;
        this.groupIds = groupIds;
        this.roles = roles;
    }

    public UUID getDcUserId() {
        return dcUserId;
    }

    public String getUsername() {
        return username;
    }

    public List<UUID> getGroupIds() {
        return groupIds;
    }

    public List<String> getRoles() {
        return roles;
    }
}
