package com.texora.secops.dc.config.service;

import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcConfigApproval;
import com.texora.secops.dc.domain.DcConfigItem;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.ConfigApprovalRepository;
import com.texora.secops.dc.repository.ConfigItemRepository;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.TenantScopedLookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration change workflow with the mandatory TWO-PERSON rule (LLD 5.3, DC-F-004). Enforced HERE, in the
 * service layer (B.6.5), not in documentation or only by RBAC:
 * <ul>
 *   <li>submit: DRAFT -> PENDING_APPROVAL (a freshly created item has no prior approvers, so the "submitter is not a
 *       prior approver" check is trivially true here and is enforced for real at decision time);</li>
 *   <li>decide: the submitter can NEVER decide their own item; an approver can decide an item at most once; an item
 *       can only be decided while PENDING_APPROVAL (row-locked, so concurrent approvers cannot both win).</li>
 * </ul>
 * Every transition is audited. SIEM alerting: each REJECTED is an audit event with the submitter in the payload, and when
 * the same submitter reaches the configured threshold of rejections in the window a dedicated
 * CONFIG_REPEATED_REJECTIONS event (outcome ALERT) is emitted for the SIEM to alert on.
 */
@Service
public class ConfigApprovalService {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private final ConfigItemRepository items;
    private final ConfigApprovalRepository approvals;
    private final DcAuditRecorder audit;
    private final TenantScopedLookup lookup;
    private final TimeSource time;
    private final int rejectionThreshold;
    private final long rejectionWindowHours;

    public ConfigApprovalService(ConfigItemRepository items, ConfigApprovalRepository approvals, DcAuditRecorder audit,
                                 TenantScopedLookup lookup, TimeSource time,
                                 @Value("${dc.config.repeated-rejection-threshold:3}") int rejectionThreshold,
                                 @Value("${dc.config.repeated-rejection-window-hours:24}") long rejectionWindowHours) {
        this.items = items;
        this.approvals = approvals;
        this.audit = audit;
        this.lookup = lookup;
        this.time = time;
        this.rejectionThreshold = rejectionThreshold;
        this.rejectionWindowHours = rejectionWindowHours;
    }

    @Transactional(readOnly = true)
    public Page<DcConfigItem> list(UUID tenantId, String status, Pageable pageable) {
        if (status == null || status.trim().isEmpty()) {
            return items.findByTenantId(tenantId, pageable);
        }
        return items.findByTenantIdAndStatus(tenantId, status, pageable);
    }

    @Transactional
    public DcConfigItem submit(CallerContext caller, String key, String value) {
        if (looksLikeSecret(key)) {
            // B.6.6: no secrets in config - they belong in SEC / the vault.
            throw DcException.unprocessable("SECRET_IN_CONFIG", "Secrets must not be stored as configuration; use the Security Server");
        }
        UUID tenantId = caller.getTenantId();
        Instant now = time.now();
        DcConfigItem item = new DcConfigItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantId);
        item.setConfigKey(key);
        item.setConfigValue(value);
        item.setStatus(DRAFT);
        item.setSubmittedBy(caller.getSubjectId());
        item.setSubmittedAt(now);
        item.setUpdatedAt(now);
        items.save(item);
        auditTransition(caller, item, "CONFIG_DRAFTED", "SUCCESS", null);

        item.setStatus(PENDING_APPROVAL);
        item.setUpdatedAt(time.now());
        items.save(item);
        auditTransition(caller, item, "CONFIG_PENDING_APPROVAL", "SUCCESS", null);
        return item;
    }

    @Transactional
    public DcConfigItem decide(CallerContext caller, UUID itemId, String decision, String reason) {
        UUID tenantId = caller.getTenantId();
        if (!APPROVED.equals(decision) && !REJECTED.equals(decision)) {
            throw DcException.unprocessable("INVALID_DECISION", "decision must be APPROVED or REJECTED");
        }
        if (REJECTED.equals(decision) && (reason == null || reason.trim().isEmpty())) {
            throw DcException.unprocessable("REASON_REQUIRED", "A reason is required when rejecting");
        }
        DcConfigItem item = lookup.require(items.findByIdForUpdate(tenantId, itemId), DcConfigItem.class, itemId, caller);

        // TWO-PERSON RULE: the submitter can never decide their own change. Denial is audited and committed independently.
        if (item.getSubmittedBy().equals(caller.getSubjectId())) {
            auditDenied(caller, item, "SELF_APPROVAL_FORBIDDEN");
            throw DcException.forbidden("SELF_APPROVAL_FORBIDDEN", "The submitter cannot decide their own configuration change");
        }
        if (!PENDING_APPROVAL.equals(item.getStatus())) {
            throw DcException.conflict("NOT_PENDING", "Configuration item is not pending approval (status " + item.getStatus() + ")");
        }
        if (approvals.existsByTenantIdAndConfigItemIdAndApproverId(tenantId, itemId, caller.getSubjectId())) {
            throw DcException.conflict("ALREADY_DECIDED", "You have already decided this item");
        }

        Instant now = time.now();
        DcConfigApproval approval = new DcConfigApproval();
        approval.setId(UUID.randomUUID());
        approval.setTenantId(tenantId);
        approval.setConfigItemId(itemId);
        approval.setApproverId(caller.getSubjectId());
        approval.setDecision(decision);
        approval.setReason(reason);
        approval.setDecidedAt(now);
        approvals.save(approval);

        item.setStatus(decision);
        item.setUpdatedAt(now);
        if (APPROVED.equals(decision)) {
            item.setActivatedAt(now); // value activated
        } else {
            item.setRejectionReason(reason);
        }
        items.save(item);
        auditTransition(caller, item, APPROVED.equals(decision) ? "CONFIG_APPROVED" : "CONFIG_REJECTED", "SUCCESS", reason);

        if (REJECTED.equals(decision)) {
            alertOnRepeatedRejections(caller, item, now);
        }
        return item;
    }

    private void alertOnRepeatedRejections(CallerContext caller, DcConfigItem item, Instant now) {
        long count = items.countByTenantIdAndSubmittedByAndStatusAndUpdatedAtAfter(
                item.getTenantId(), item.getSubmittedBy(), REJECTED, now.minusSeconds(rejectionWindowHours * 3600L));
        if (count >= rejectionThreshold) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("submitterId", item.getSubmittedBy().toString());
            payload.put("rejections", count);
            payload.put("windowHours", rejectionWindowHours);
            audit.record(item.getTenantId(), caller.getSubjectId(), "CONFIG_REPEATED_REJECTIONS", "CONFIG_ITEM",
                    item.getId().toString(), "ALERT", payload);
        }
    }

    private void auditTransition(CallerContext caller, DcConfigItem item, String action, String outcome, String reason) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("key", item.getConfigKey()); // never the value: it may be sensitive
        payload.put("status", item.getStatus());
        payload.put("submitterId", item.getSubmittedBy().toString());
        if (reason != null) {
            payload.put("reason", reason);
        }
        audit.record(item.getTenantId(), caller.getSubjectId(), action, "CONFIG_ITEM", item.getId().toString(), outcome, payload);
    }

    private void auditDenied(CallerContext caller, DcConfigItem item, String code) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("key", item.getConfigKey());
        payload.put("code", code);
        payload.put("submitterId", item.getSubmittedBy().toString());
        audit.recordIndependent(item.getTenantId(), caller.getSubjectId(), "CONFIG_DECISION_DENIED", "CONFIG_ITEM",
                item.getId().toString(), "DENIED", payload);
    }

    private boolean looksLikeSecret(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("password") || k.contains("secret") || k.contains("token") || k.contains("apikey")
                || k.contains("api-key") || k.contains("api_key") || k.contains("private-key") || k.contains("private_key");
    }
}
