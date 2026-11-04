package com.texora.secops.siem.alerting;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.siem.alerting.service.AlertLifecycleService;
import com.texora.secops.siem.audit.AuditEventPublisher;
import com.texora.secops.siem.domain.SiemAlert;
import com.texora.secops.siem.domain.enums.AlertSeverity;
import com.texora.secops.siem.domain.enums.AlertStatus;
import com.texora.secops.siem.repository.SiemAlertRepository;
import com.texora.secops.siem.repository.SiemAlertTransitionRepository;
import com.texora.secops.siem.security.CrossTenantAccessException;
import com.texora.secops.siem.security.CallerContext;
import com.texora.secops.siem.web.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertLifecycleServiceTest {

    private SiemAlertRepository alertRepository;
    private SiemAlertTransitionRepository transitionRepository;
    private AlertLifecycleService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        alertRepository = mock(SiemAlertRepository.class);
        transitionRepository = mock(SiemAlertTransitionRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        service = new AlertLifecycleService(alertRepository, transitionRepository, auditEventPublisher);

        tenantId = UUID.randomUUID();
        // TokenClaims (com.texora.secops.iam.token) is a confirmed `final class` exposing only
        // getters + hasRole() - no public constructor is given in KNOWN-INTEGRATION-PITFALLS.md,
        // so instances are almost certainly built internally by TokenValidator only. Mockito's
        // default inline mock maker (Mockito 5+, pulled in via spring-boot-starter-test) can mock
        // final classes, so we stub it rather than guess a constructor that may not exist.
        TokenClaims caller = mock(TokenClaims.class);
        when(caller.getTenantId()).thenReturn(tenantId);
        when(caller.getSubject()).thenReturn(UUID.randomUUID());
        when(caller.getRoles()).thenReturn(Set.of("siem.alert.write"));
        CallerContext.set(caller);

        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CallerContext.clear();
    }

    private SiemAlert openAlert() {
        return new SiemAlert(UUID.randomUUID(), tenantId, UUID.randomUUID(), Instant.now(),
                AlertSeverity.HIGH, AlertStatus.OPEN, tenantId, "evt-1", "summary");
    }

    @Test
    void openToTriagedIsAllowed() {
        SiemAlert alert = openAlert();
        when(alertRepository.findByIdRaw(alert.getId())).thenReturn(Optional.of(alert));

        SiemAlert result = service.transition(alert.getId(), AlertStatus.TRIAGED, "looking into it");

        assertThat(result.getStatus()).isEqualTo(AlertStatus.TRIAGED);
    }

    @Test
    void openToEscalatedDirectlyIsRejected() {
        SiemAlert alert = openAlert();
        when(alertRepository.findByIdRaw(alert.getId())).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.transition(alert.getId(), AlertStatus.ESCALATED, "skip ahead"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void closedIsTerminal() {
        SiemAlert alert = openAlert();
        alert.setStatus(AlertStatus.CLOSED);
        when(alertRepository.findByIdRaw(alert.getId())).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.transition(alert.getId(), AlertStatus.TRIAGED, "reopen?"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void fetchingAnotherTenantsAlertIsForbiddenNotEmpty() {
        SiemAlert alert = openAlert();
        alert.setTenantId(UUID.randomUUID()); // belongs to a different tenant than the caller
        when(alertRepository.findByIdRaw(alert.getId())).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.get(alert.getId(), tenantId))
                .isInstanceOf(CrossTenantAccessException.class);
    }
}
