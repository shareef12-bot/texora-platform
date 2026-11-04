package com.texora.secops.siem.alerting.service;

import com.texora.secops.siem.alerting.model.AlertTransitionRules;
import com.texora.secops.siem.audit.AuditEventPublisher;
import com.texora.secops.siem.audit.SystemActor;
import com.texora.secops.siem.domain.SiemAlert;
import com.texora.secops.siem.domain.SiemAlertTransition;
import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.enums.AlertSeverity;
import com.texora.secops.siem.domain.enums.AlertStatus;
import com.texora.secops.siem.domain.enums.AuditOutcome;
import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.repository.SiemAlertRepository;
import com.texora.secops.siem.repository.SiemAlertTransitionRepository;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.siem.security.CrossTenantAccessException;
import com.texora.secops.siem.security.CallerContext;
import com.texora.secops.siem.web.BusinessRuleException;
import com.texora.secops.siem.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pipeline steps 5-6. Alert creation (step 5, triggered by {@code RuleExecutionSandbox} on a
 * rule match) is a system action audited under {@link SystemActor#ID}. Every subsequent triage
 * transition is a caller action, validated against {@link AlertTransitionRules} and recorded to
 * {@code siem_alert_transition} - the full history of an alert's life is always reconstructable.
 */
@Service
public class AlertLifecycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertLifecycleService.class);

    private final SiemAlertRepository alertRepository;
    private final SiemAlertTransitionRepository transitionRepository;
    private final AuditEventPublisher auditEventPublisher;

    public AlertLifecycleService(SiemAlertRepository alertRepository,
                                  SiemAlertTransitionRepository transitionRepository,
                                  AuditEventPublisher auditEventPublisher) {
        this.alertRepository = alertRepository;
        this.transitionRepository = transitionRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    /** Called by RuleExecutionSandbox on a rule match. Not annotated @Audited (that aspect
     *  assumes an HTTP-bound caller identity); audited directly here under the system actor. */
    @Transactional
    public SiemAlert createAlert(SiemCorrelationRule rule, NormalizedEvent triggeringEvent, AlertSeverity severity) {
        SiemAlert alert = new SiemAlert(UUID.randomUUID(), rule.getTenantId(), rule.getId(), Instant.now(),
                severity, AlertStatus.OPEN, triggeringEvent.getTenantScope(), triggeringEvent.getId(),
                "Rule '" + rule.getName() + "' matched on " + triggeringEvent.getSourceService()
                        + " event " + triggeringEvent.getEventType());
        alertRepository.save(alert);

        auditEventPublisher.publish(alert.getTenantId(), SystemActor.ID, "alert.created", "SiemAlert",
                alert.getId().toString(), AuditOutcome.SUCCESS, alert);
        LOGGER.info("Alert {} created (severity={}) from rule {} on tenant {}",
                alert.getId(), severity, rule.getId(), alert.getTenantId());
        return alert;
    }

    public List<SiemAlert> list(UUID tenantId) {
        return alertRepository.findAllByTenantId(tenantId);
    }

    public SiemAlert get(UUID id, UUID tenantId) {
        SiemAlert alert = alertRepository.findByIdRaw(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert " + id + " not found"));
        if (!alert.getTenantId().equals(tenantId)) {
            // Cross-tenant access: 403, never a distinguishing 404 (B.5 / Requirement 3).
            throw new CrossTenantAccessException("Alert " + id + " does not belong to caller's tenant");
        }
        return alert;
    }

    @Transactional
    public SiemAlert transition(UUID alertId, AlertStatus toStatus, String notes) {
        TokenClaims caller = CallerContext.get();
        SiemAlert alert = get(alertId, caller.getTenantId());

        if (!AlertTransitionRules.isAllowed(alert.getStatus(), toStatus)) {
            throw new BusinessRuleException("ALERT_ILLEGAL_TRANSITION",
                    "Cannot transition alert from " + alert.getStatus() + " to " + toStatus);
        }

        AlertStatus fromStatus = alert.getStatus();
        alert.setStatus(toStatus);
        alertRepository.save(alert);

        SiemAlertTransition transition = new SiemAlertTransition(UUID.randomUUID(), alert.getTenantId(),
                alert.getId(), fromStatus, toStatus, caller.getSubject(), Instant.now(), notes);
        transitionRepository.save(transition);

        auditEventPublisher.publish(alert.getTenantId(), caller.getSubject(), "alert.transition", "SiemAlert",
                alert.getId().toString(), AuditOutcome.SUCCESS, transition);
        return alert;
    }

    public List<SiemAlertTransition> history(UUID alertId, UUID tenantId) {
        get(alertId, tenantId); // enforces tenant check / 403 before revealing any history
        return transitionRepository.findAllByAlertIdOrderByTransitionedAtAsc(alertId);
    }
}
