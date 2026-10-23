package com.texora.secops.dc.admin.service;

import com.texora.secops.dc.DcConstants;
import com.texora.secops.dc.adapter.HrmsEmployee;
import com.texora.secops.dc.adapter.HrmsEmployeeAdapter;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * User lifecycle under ADR D-2: HRMS owns employee identity, so DC does NOT author employees. dc_user is a
 * read-mostly projection of HRMS data; DC only owns the security-lock overlay and security-group membership.
 * All HRMS access goes through the single {@link HrmsEmployeeAdapter} (currently a flagged local stub).
 */
@Service
public class UserLifecycleService {

    public enum ApplyResult { CREATED, UPDATED, UNCHANGED, SKIPPED_STALE }

    private final UserRepository users;
    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final HrmsEmployeeAdapter hrms;
    private final DirectorySyncOrchestrator sync;
    private final DcAuditRecorder audit;
    private final TenantScopedLookup lookup;
    private final TimeSource time;

    public UserLifecycleService(UserRepository users, GroupRepository groups, MembershipRepository memberships,
                                HrmsEmployeeAdapter hrms, DirectorySyncOrchestrator sync, DcAuditRecorder audit,
                                TenantScopedLookup lookup, TimeSource time) {
        this.users = users;
        this.groups = groups;
        this.memberships = memberships;
        this.hrms = hrms;
        this.sync = sync;
        this.audit = audit;
        this.lookup = lookup;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Page<DcUser> list(UUID tenantId, Pageable pageable) {
        return users.findByTenantId(tenantId, pageable);
    }

    /** POST /api/v1/users: pull ONE employee from HRMS into the projection now (ahead of the scheduled pull). */
    @Transactional
    public DcUser materialize(CallerContext caller, String hrmsEmployeeId, List<String> initialGroups) {
        UUID tenantId = caller.getTenantId();
        HrmsEmployee employee;
        try {
            employee = hrms.fetchEmployee(tenantId, hrmsEmployeeId).orElse(null);
        } catch (RuntimeException e) {
            throw DcException.upstreamTimeout("HRMS is unavailable");
        }
        if (employee == null) {
            throw DcException.unprocessable("HRMS_EMPLOYEE_NOT_FOUND", "No such employee in HRMS");
        }
        if (users.findByTenantIdAndHrmsEmployeeId(tenantId, hrmsEmployeeId).isPresent()) {
            throw DcException.conflict("USER_EXISTS", "Employee is already projected into DC");
        }
        if (users.findByTenantIdAndUsername(tenantId, employee.getUsername()).isPresent()) {
            throw DcException.conflict("USERNAME_EXISTS", "Username already exists");
        }
        List<DcGroup> resolved = new ArrayList<DcGroup>();
        if (initialGroups != null) {
            for (String name : initialGroups) {
                DcGroup g = groups.findByTenantIdAndName(tenantId, name)
                        .orElseThrow(() -> DcException.unprocessable("GROUP_NOT_FOUND", "Unknown group: " + name));
                resolved.add(g);
            }
        }
        DcUser user = create(tenantId, caller.getSubjectId(), employee, "USER_MATERIALIZED");
        for (DcGroup g : resolved) {
            addMembership(tenantId, caller.getSubjectId(), g, user);
        }
        return user;
    }

    /** PATCH /api/v1/users/{id}: the only DC-owned user attribute is the security lock (everything else is HRMS-owned). */
    @Transactional
    public DcUser setSecurityLock(CallerContext caller, UUID userId, boolean locked) {
        DcUser user = lookup.require(users.findByTenantIdAndId(caller.getTenantId(), userId), DcUser.class, userId, caller);
        if (user.isSecurityLocked() == locked) {
            return user;
        }
        user.setSecurityLocked(locked);
        user.setUpdatedAt(time.now());
        users.save(user);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("securityLocked", locked);
        audit.record(caller.getTenantId(), caller.getSubjectId(), locked ? "USER_SECURITY_LOCKED" : "USER_SECURITY_UNLOCKED",
                "USER", user.getId().toString(), "SUCCESS", payload);
        sync.requestSync(caller.getTenantId(), "USER", user.getId().toString(), "UPSERT",
                SyncPayloads.userKey(user, "UPSERT"), caller.getSubjectId(), SyncPayloads.user(user));
        return user;
    }

    /**
     * Upsert one HRMS employee into the projection. Idempotent and monotonic: an update whose lastModified is not
     * newer than what DC already holds is ignored (SKIPPED_STALE), so replays/out-of-order delivery cannot regress data.
     * Runs in its own transaction per employee so one bad record cannot poison a whole pull.
     */
    @Transactional
    public ApplyResult applyHrmsSnapshot(UUID tenantId, UUID actorId, HrmsEmployee e) {
        Optional<DcUser> found = users.findByTenantIdAndHrmsEmployeeId(tenantId, e.getHrmsEmployeeId());
        if (!found.isPresent()) {
            if (users.findByTenantIdAndUsername(tenantId, e.getUsername()).isPresent()) {
                throw DcException.conflict("USERNAME_EXISTS", "Username '" + e.getUsername() + "' already used by another employee");
            }
            create(tenantId, actorId, e, "USER_PROJECTED");
            return ApplyResult.CREATED;
        }
        DcUser user = found.get();
        if (user.getHrmsLastModified() != null && !e.getLastModified().isAfter(user.getHrmsLastModified())) {
            return ApplyResult.SKIPPED_STALE;
        }
        boolean changed = !Objects.equals(user.getUsername(), e.getUsername())
                || !Objects.equals(user.getDisplayName(), e.getDisplayName())
                || !Objects.equals(user.getOrgUnitId(), e.getOrgUnitId())
                || !Objects.equals(user.getStatus(), e.getStatus());
        if (changed && !Objects.equals(user.getUsername(), e.getUsername())) {
            Optional<DcUser> clash = users.findByTenantIdAndUsername(tenantId, e.getUsername());
            if (clash.isPresent() && !clash.get().getId().equals(user.getId())) {
                throw DcException.conflict("USERNAME_EXISTS", "Username '" + e.getUsername() + "' already used by another employee");
            }
        }
        Instant now = time.now();
        user.setHrmsLastModified(e.getLastModified());
        if (!changed) {
            users.save(user);
            return ApplyResult.UNCHANGED;
        }
        user.setUsername(e.getUsername());
        user.setDisplayName(e.getDisplayName());
        user.setOrgUnitId(e.getOrgUnitId());
        user.setStatus(e.getStatus());
        user.setUpdatedAt(now);
        users.save(user);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("hrmsEmployeeId", e.getHrmsEmployeeId());
        payload.put("status", e.getStatus());
        audit.record(tenantId, actorId, "USER_PROJECTION_UPDATED", "USER", user.getId().toString(), "SUCCESS", payload);
        sync.requestSync(tenantId, "USER", user.getId().toString(), "UPSERT", SyncPayloads.userKey(user, "UPSERT"),
                actorId, SyncPayloads.user(user));
        return ApplyResult.UPDATED;
    }

    private DcUser create(UUID tenantId, UUID actorId, HrmsEmployee e, String auditAction) {
        Instant now = time.now();
        DcUser user = new DcUser();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setUsername(e.getUsername());
        user.setStatus(e.getStatus());
        user.setDisplayName(e.getDisplayName());
        user.setOrgUnitId(e.getOrgUnitId());
        user.setHrmsEmployeeId(e.getHrmsEmployeeId());
        user.setHrmsLastModified(e.getLastModified());
        user.setSecurityLocked(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.save(user);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("hrmsEmployeeId", e.getHrmsEmployeeId());
        audit.record(tenantId, actorId, auditAction, "USER", user.getId().toString(), "SUCCESS", payload);
        sync.requestSync(tenantId, "USER", user.getId().toString(), "UPSERT", SyncPayloads.userKey(user, "UPSERT"),
                actorId, SyncPayloads.user(user));
        return user;
    }

    private void addMembership(UUID tenantId, UUID actorId, DcGroup group, DcUser user) {
        DcGroupMembership m = new DcGroupMembership(new DcGroupMembershipId(user.getId(), group.getId()), tenantId, actorId, time.now());
        memberships.save(m);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("groupId", group.getId().toString());
        payload.put("userId", user.getId().toString());
        audit.record(tenantId, actorId, "GROUP_MEMBER_ADDED", "GROUP", group.getId().toString(), "SUCCESS", payload);
        sync.requestSync(tenantId, "GROUP_MEMBERSHIP", group.getId() + ":" + user.getId(), "ADD",
                SyncPayloads.membershipKey(group, user, "ADD"), actorId, SyncPayloads.membership(group, user));
    }

    /** Exposed for the scheduled pull's system actor. */
    public static UUID systemActor() {
        return DcConstants.SYSTEM_ACTOR;
    }
}
