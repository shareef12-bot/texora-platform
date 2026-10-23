package com.texora.secops.dc.dto;

import com.texora.secops.dc.admin.dto.GroupMembershipResponse;
import com.texora.secops.dc.admin.dto.GroupResponse;
import com.texora.secops.dc.admin.dto.UserResponse;
import com.texora.secops.dc.audit.dto.AuditEventResponse;
import com.texora.secops.dc.config.dto.ConfigResponse;
import com.texora.secops.dc.domain.DcAuditEvent;
import com.texora.secops.dc.domain.DcConfigItem;
import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcGroupMembership;
import com.texora.secops.dc.domain.DcPolicy;
import com.texora.secops.dc.domain.DcPolicyAssignment;
import com.texora.secops.dc.domain.DcSyncJob;
import com.texora.secops.dc.policy.dto.PolicyAssignmentResponse;
import com.texora.secops.dc.policy.dto.PolicyResponse;
import com.texora.secops.dc.sync.dto.SyncJobResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Entity -> DTO mapping. Entities are never serialized over HTTP (B.3). */
public final class DtoMapper {

    public static final int MAX_PAGE_SIZE = 200;

    private DtoMapper() {
    }

    public static Pageable pageable(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }

    public static <E, R> PageResponse<R> page(Page<E> page, Function<E, R> mapper) {
        List<R> items = new ArrayList<R>();
        for (E e : page.getContent()) {
            items.add(mapper.apply(e));
        }
        return new PageResponse<R>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static UserResponse user(com.texora.secops.dc.domain.DcUser u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getStatus(), u.getDisplayName(), u.getOrgUnitId(),
                u.getHrmsEmployeeId(), u.isSecurityLocked(), "HRMS", u.getCreatedAt(), u.getUpdatedAt());
    }

    public static GroupResponse group(DcGroup g) {
        return new GroupResponse(g.getId(), g.getName(), g.getDescription(), g.getOrgUnitId(), g.getCreatedAt(), g.getUpdatedAt());
    }

    public static GroupMembershipResponse membership(DcGroupMembership m) {
        return new GroupMembershipResponse(m.getId().getGroupId(), m.getId().getUserId(), m.getAssignedBy(), m.getAssignedAt());
    }

    public static PolicyResponse policy(DcPolicy p) {
        return new PolicyResponse(p.getId(), p.getSubjectType(), p.getResource(), p.getAction(), p.getEffect(), p.getVersion(), p.getCreatedAt());
    }

    public static PolicyAssignmentResponse assignment(DcPolicyAssignment a) {
        return new PolicyAssignmentResponse(a.getId(), a.getPolicyId(), a.getSubjectType(), a.getSubjectId(), a.getAssignedBy(), a.getAssignedAt());
    }

    public static ConfigResponse config(DcConfigItem c) {
        return new ConfigResponse(c.getId(), c.getConfigKey(), c.getConfigValue(), c.getStatus(), c.getSubmittedBy(),
                c.getSubmittedAt(), c.getUpdatedAt(), c.getRejectionReason(), c.getActivatedAt());
    }

    public static SyncJobResponse syncJob(DcSyncJob j) {
        return new SyncJobResponse(j.getId(), j.getSource(), j.getEntityType(), j.getStatus(), j.getRecordsProcessed(),
                j.getStartedAt(), j.getCompletedAt(), j.getErrorSummary());
    }

    public static AuditEventResponse audit(DcAuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getActorId(), e.getAction(), e.getTargetType(), e.getTargetId(),
                e.getOutcome(), e.getOccurredAt(), e.getPayload());
    }
}
