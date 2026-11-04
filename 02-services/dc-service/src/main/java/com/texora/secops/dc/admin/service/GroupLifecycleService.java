package com.texora.secops.dc.admin.service;

import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcGroupMembership;
import com.texora.secops.dc.domain.DcGroupMembershipId;
import com.texora.secops.dc.domain.DcUser;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.GroupRepository;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.UserRepository;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.TenantScopedLookup;
import com.texora.secops.dc.sync.service.DirectorySyncOrchestrator;
import com.texora.secops.dc.sync.service.SyncPayloads;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** SECURITY groups (vpn-admins, siem-analysts ...) owned by DC - distinct from HRMS org-chart groupings (ADR D-2). */
@Service
public class GroupLifecycleService {

    private final GroupRepository groups;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final DirectorySyncOrchestrator sync;
    private final DcAuditRecorder audit;
    private final TenantScopedLookup lookup;
    private final TimeSource time;

    public GroupLifecycleService(GroupRepository groups, UserRepository users, MembershipRepository memberships,
                                 DirectorySyncOrchestrator sync, DcAuditRecorder audit, TenantScopedLookup lookup, TimeSource time) {
        this.groups = groups;
        this.users = users;
        this.memberships = memberships;
        this.sync = sync;
        this.audit = audit;
        this.lookup = lookup;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Page<DcGroup> list(UUID tenantId, Pageable pageable) {
        return groups.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public DcGroup create(CallerContext caller, String name, String description, String orgUnitId) {
        UUID tenantId = caller.getTenantId();
        if (groups.findByTenantIdAndName(tenantId, name).isPresent()) {
            throw DcException.conflict("GROUP_EXISTS", "Group name already exists");
        }
        Instant now = time.now();
        DcGroup group = new DcGroup();
        group.setId(UUID.randomUUID());
        group.setTenantId(tenantId);
        group.setName(name);
        group.setDescription(description);
        group.setOrgUnitId(orgUnitId);
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        groups.save(group);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("name", name);
        audit.record(tenantId, caller.getSubjectId(), "GROUP_CREATED", "GROUP", group.getId().toString(), "SUCCESS", payload);
        sync.requestSync(tenantId, "GROUP", group.getId().toString(), "UPSERT", SyncPayloads.groupKey(group, "UPSERT"),
                caller.getSubjectId(), SyncPayloads.group(group));
        return group;
    }

    /**
     * Adds a member. Group membership can carry policy (privilege), so a caller may not add THEMSELVES to a group
     * (privilege-escalation guard; ASSUMES the token subject equals the dc_user id - see README).
     */
    @Transactional
    public DcGroupMembership addMember(CallerContext caller, UUID groupId, UUID userId) {
        UUID tenantId = caller.getTenantId();
        DcGroup group = lookup.require(groups.findByTenantIdAndId(tenantId, groupId), DcGroup.class, groupId, caller);
        DcUser user = lookup.require(users.findByTenantIdAndId(tenantId, userId), DcUser.class, userId, caller);
        if (userId.equals(caller.getSubjectId())) {
            Map<String, Object> denied = new HashMap<String, Object>();
            denied.put("groupId", groupId.toString());
            audit.recordIndependent(tenantId, caller.getSubjectId(), "GROUP_MEMBER_ADD_DENIED", "GROUP",
                    groupId.toString(), "DENIED", denied);
            throw DcException.forbidden("SELF_MEMBERSHIP_FORBIDDEN", "You cannot add yourself to a group");
        }
        if (memberships.existsMembership(tenantId, groupId, userId)) {
            throw DcException.conflict("MEMBER_EXISTS", "User is already a member of this group");
        }
        DcGroupMembership membership = new DcGroupMembership(new DcGroupMembershipId(userId, groupId), tenantId,
                caller.getSubjectId(), time.now());
        memberships.save(membership);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("groupId", groupId.toString());
        payload.put("userId", userId.toString());
        audit.record(tenantId, caller.getSubjectId(), "GROUP_MEMBER_ADDED", "GROUP", groupId.toString(), "SUCCESS", payload);
        sync.requestSync(tenantId, "GROUP_MEMBERSHIP", groupId + ":" + userId, "ADD",
                SyncPayloads.membershipKey(group, user, "ADD"), caller.getSubjectId(), SyncPayloads.membership(group, user));
        return membership;
    }
}
