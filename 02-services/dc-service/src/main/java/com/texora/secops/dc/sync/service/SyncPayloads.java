package com.texora.secops.dc.sync.service;

import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcUser;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the (signed) payloads and idempotency keys DC sends to LDAP. Keeps the wire shape in one place. */
public final class SyncPayloads {

    private SyncPayloads() {
    }

    /** A user is enabled in the directory only if HRMS says ACTIVE and DC has not security-locked it. */
    public static boolean enabled(DcUser user) {
        return "ACTIVE".equalsIgnoreCase(user.getStatus()) && !user.isSecurityLocked();
    }

    public static Map<String, Object> user(DcUser user) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("userId", user.getId().toString());
        p.put("username", user.getUsername());
        p.put("displayName", user.getDisplayName());
        p.put("orgUnitId", user.getOrgUnitId());
        p.put("hrmsEmployeeId", user.getHrmsEmployeeId());
        p.put("enabled", enabled(user));
        return p;
    }

    public static Map<String, Object> group(DcGroup group) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("groupId", group.getId().toString());
        p.put("name", group.getName());
        p.put("description", group.getDescription());
        p.put("orgUnitId", group.getOrgUnitId());
        return p;
    }

    public static Map<String, Object> membership(DcGroup group, DcUser user) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("groupId", group.getId().toString());
        p.put("groupName", group.getName());
        p.put("userId", user.getId().toString());
        p.put("memberUsername", user.getUsername());
        return p;
    }

    /** Idempotency keys: the same logical mutation (same entity version) can never create two jobs. */
    public static String userKey(DcUser user, String operation) {
        return "USER:" + user.getId() + ":" + operation + ":" + user.getUpdatedAt();
    }

    public static String groupKey(DcGroup group, String operation) {
        return "GROUP:" + group.getId() + ":" + operation + ":" + group.getUpdatedAt();
    }

    public static String membershipKey(DcGroup group, DcUser user, String operation) {
        return "GROUP_MEMBERSHIP:" + group.getId() + ":" + user.getId() + ":" + operation;
    }
}
