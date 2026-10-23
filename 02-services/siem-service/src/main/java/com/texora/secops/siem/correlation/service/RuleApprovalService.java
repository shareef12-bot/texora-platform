package com.texora.secops.siem.correlation.service;

import com.texora.secops.siem.audit.AuditEventPublisher;
import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.SiemRuleChangeAudit;
import com.texora.secops.siem.domain.enums.AuditOutcome;
import com.texora.secops.siem.domain.enums.RuleChangeType;
import com.texora.secops.siem.domain.enums.RuleStatus;
import com.texora.secops.siem.repository.SiemCorrelationRuleRepository;
import com.texora.secops.siem.repository.SiemRuleChangeAuditRepository;
import com.texora.secops.siem.security.CrossTenantAccessException;
import com.texora.secops.siem.web.BusinessRuleException;
import com.texora.secops.siem.web.ConflictException;
import com.texora.secops.siem.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REQUIREMENT 4 - RULE APPROVAL (two-person). A change is either a brand-new rule submission
 * (targetRuleId null) or a request to disable an existing APPROVED rule (targetRuleId set) - a
 * disable is exactly as consequential as a new detection and goes through the identical
 * approval gate. The submitter can never be the approver, checked here in the service layer
 * (not left to documentation, per B.6.5), and every decision - approve or reject - is written to
 * {@code siem_rule_change_audit} before this method returns, regardless of outcome.
 */
@Service
public class RuleApprovalService {

    private final SiemCorrelationRuleRepository ruleRepository;
    private final SiemRuleChangeAuditRepository changeAuditRepository;
    private final RuleDefinitionParser ruleDefinitionParser;
    private final AuditEventPublisher auditEventPublisher;

    public RuleApprovalService(SiemCorrelationRuleRepository ruleRepository,
                                SiemRuleChangeAuditRepository changeAuditRepository,
                                RuleDefinitionParser ruleDefinitionParser,
                                AuditEventPublisher auditEventPublisher) {
        this.ruleRepository = ruleRepository;
        this.changeAuditRepository = changeAuditRepository;
        this.ruleDefinitionParser = ruleDefinitionParser;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public SiemCorrelationRule submitNewRule(UUID tenantId, UUID callerId, String name, String ruleDefinitionJson) {
        ruleDefinitionParser.parse(ruleDefinitionJson); // fail fast on a malformed definition at submission time
        Instant now = Instant.now();
        SiemCorrelationRule rule = new SiemCorrelationRule(UUID.randomUUID(), tenantId, name, ruleDefinitionJson,
                RuleStatus.PENDING_APPROVAL, callerId, null, now, now);
        ruleRepository.save(rule);
        recordChange(rule.getId(), tenantId, callerId, RuleChangeType.SUBMITTED, "New rule submitted: " + name);
        return rule;
    }

    @Transactional
    public SiemCorrelationRule submitDisableRequest(UUID tenantId, UUID callerId, UUID targetRuleId) {
        SiemCorrelationRule target = requireOwnedByTenant(targetRuleId, tenantId);
        if (target.getStatus() != RuleStatus.APPROVED) {
            throw new BusinessRuleException("RULE_NOT_APPROVED", "Only an APPROVED rule can have a disable requested");
        }
        Instant now = Instant.now();
        SiemCorrelationRule changeRequest = new SiemCorrelationRule(UUID.randomUUID(), tenantId,
                "[disable-request] " + target.getName(), target.getRuleDefinition(),
                RuleStatus.PENDING_APPROVAL, callerId, targetRuleId, now, now);
        ruleRepository.save(changeRequest);
        recordChange(changeRequest.getId(), tenantId, callerId, RuleChangeType.SUBMITTED,
                "Disable requested for rule " + targetRuleId);
        return changeRequest;
    }

    @Transactional
    public SiemCorrelationRule decide(UUID tenantId, UUID callerId, UUID changeRequestId, boolean approve, String notes) {
        SiemCorrelationRule changeRequest = requireOwnedByTenant(changeRequestId, tenantId);

        if (changeRequest.getStatus() != RuleStatus.PENDING_APPROVAL) {
            throw new ConflictException("Rule change " + changeRequestId + " is not pending approval");
        }
        if (changeRequest.getSubmittedBy().equals(callerId)) {
            // The core of Requirement 4: enforced here, not left to a documented policy.
            recordChange(changeRequest.getId(), tenantId, callerId, RuleChangeType.REJECTED,
                    "Blocked: submitter attempted to self-approve");
            throw new BusinessRuleException("RULE_SELF_APPROVAL_DENIED", "Submitter cannot approve their own change");
        }

        Instant now = Instant.now();
        changeRequest.setDecisionBy(callerId);
        changeRequest.setDecisionAt(now);
        changeRequest.setDecisionNotes(notes);

        if (approve) {
            if (changeRequest.getTargetRuleId() != null) {
                SiemCorrelationRule target = requireOwnedByTenant(changeRequest.getTargetRuleId(), tenantId);
                target.setStatus(RuleStatus.DISABLED);
                target.setUpdatedAt(now);
                ruleRepository.save(target);
                recordChange(target.getId(), tenantId, callerId, RuleChangeType.DISABLED, notes);
            } else {
                recordChange(changeRequest.getId(), tenantId, callerId, RuleChangeType.APPROVED, notes);
            }
            changeRequest.setStatus(RuleStatus.APPROVED);
        } else {
            changeRequest.setStatus(RuleStatus.DISABLED);
            recordChange(changeRequest.getId(), tenantId, callerId, RuleChangeType.REJECTED, notes);
        }

        changeRequest.setUpdatedAt(now);
        ruleRepository.save(changeRequest);
        return changeRequest;
    }

    public List<SiemCorrelationRule> list(UUID tenantId) {
        return ruleRepository.findAllByTenantId(tenantId);
    }

    public SiemCorrelationRule get(UUID id, UUID tenantId) {
        return requireOwnedByTenant(id, tenantId);
    }

    public List<SiemRuleChangeAudit> changeHistory(UUID ruleId, UUID tenantId) {
        requireOwnedByTenant(ruleId, tenantId); // 403 before revealing any history
        return changeAuditRepository.findAllByCorrelationRuleId(ruleId);
    }

    private SiemCorrelationRule requireOwnedByTenant(UUID id, UUID tenantId) {
        SiemCorrelationRule rule = ruleRepository.findByIdRaw(id)
                .orElseThrow(() -> new ResourceNotFoundException("Correlation rule " + id + " not found"));
        if (!rule.getTenantId().equals(tenantId)) {
            throw new CrossTenantAccessException("Correlation rule " + id + " does not belong to caller's tenant");
        }
        return rule;
    }

    private void recordChange(UUID ruleId, UUID tenantId, UUID changedBy, RuleChangeType changeType, String notes) {
        SiemRuleChangeAudit changeAudit = new SiemRuleChangeAudit(UUID.randomUUID(), tenantId, ruleId,
                changedBy, changeType, Instant.now(), notes);
        changeAuditRepository.save(changeAudit);
        auditEventPublisher.publish(tenantId, changedBy, "correlation-rule." + changeType.name().toLowerCase(),
                "SiemCorrelationRule", ruleId.toString(), AuditOutcome.SUCCESS, changeAudit);
    }
}
