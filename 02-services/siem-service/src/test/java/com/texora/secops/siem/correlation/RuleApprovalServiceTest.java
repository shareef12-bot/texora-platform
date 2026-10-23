package com.texora.secops.siem.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.siem.audit.AuditEventPublisher;
import com.texora.secops.siem.correlation.service.RuleApprovalService;
import com.texora.secops.siem.correlation.service.RuleDefinitionParser;
import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.enums.RuleChangeType;
import com.texora.secops.siem.domain.enums.RuleStatus;
import com.texora.secops.siem.repository.SiemCorrelationRuleRepository;
import com.texora.secops.siem.repository.SiemRuleChangeAuditRepository;
import com.texora.secops.siem.web.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQUIREMENT 4 - the submitter can never be the approver, and every decision is audited to
 *  siem_rule_change_audit regardless of outcome. */
class RuleApprovalServiceTest {

    private static final String VALID_DEFINITION = "{"
            + "\"conditions\":[{\"sourceService\":\"sso\",\"eventType\":\"login.failed\",\"countThreshold\":5,\"windowSeconds\":300}],"
            + "\"severity\":\"MEDIUM\"}";

    private SiemCorrelationRuleRepository ruleRepository;
    private SiemRuleChangeAuditRepository changeAuditRepository;
    private AuditEventPublisher auditEventPublisher;
    private RuleApprovalService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(SiemCorrelationRuleRepository.class);
        changeAuditRepository = mock(SiemRuleChangeAuditRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new RuleApprovalService(ruleRepository, changeAuditRepository,
                new RuleDefinitionParser(new ObjectMapper()), auditEventPublisher);

        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submitterCannotApproveTheirOwnChange() {
        UUID tenantId = UUID.randomUUID();
        UUID submitter = UUID.randomUUID();
        SiemCorrelationRule pending = new SiemCorrelationRule(UUID.randomUUID(), tenantId, "rule-a",
                VALID_DEFINITION, RuleStatus.PENDING_APPROVAL, submitter, null, Instant.now(), Instant.now());
        when(ruleRepository.findByIdRaw(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.decide(tenantId, submitter, pending.getId(), true, "trying to self-approve"))
                .isInstanceOf(BusinessRuleException.class);

        // The blocked self-approval attempt is itself audited - not silently swallowed.
        verify(changeAuditRepository).save(argThatChangeType(RuleChangeType.REJECTED));
    }

    @Test
    void aDifferentApproverCanApprove() {
        UUID tenantId = UUID.randomUUID();
        UUID submitter = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        SiemCorrelationRule pending = new SiemCorrelationRule(UUID.randomUUID(), tenantId, "rule-b",
                VALID_DEFINITION, RuleStatus.PENDING_APPROVAL, submitter, null, Instant.now(), Instant.now());
        when(ruleRepository.findByIdRaw(pending.getId())).thenReturn(Optional.of(pending));

        SiemCorrelationRule result = service.decide(tenantId, approver, pending.getId(), true, "looks fine");

        assertThat(result.getStatus()).isEqualTo(RuleStatus.APPROVED);
        verify(changeAuditRepository).save(argThatChangeType(RuleChangeType.APPROVED));
    }

    @Test
    void everyDecisionIsAuditedEvenOnRejection() {
        UUID tenantId = UUID.randomUUID();
        UUID submitter = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        SiemCorrelationRule pending = new SiemCorrelationRule(UUID.randomUUID(), tenantId, "rule-c",
                VALID_DEFINITION, RuleStatus.PENDING_APPROVAL, submitter, null, Instant.now(), Instant.now());
        when(ruleRepository.findByIdRaw(pending.getId())).thenReturn(Optional.of(pending));

        SiemCorrelationRule result = service.decide(tenantId, approver, pending.getId(), false, "not needed");

        assertThat(result.getStatus()).isEqualTo(RuleStatus.DISABLED);
        verify(changeAuditRepository).save(argThatChangeType(RuleChangeType.REJECTED));
    }

    private com.texora.secops.siem.domain.SiemRuleChangeAudit argThatChangeType(RuleChangeType type) {
        return org.mockito.ArgumentMatchers.argThat(audit -> audit != null && audit.getChangeType() == type);
    }
}
