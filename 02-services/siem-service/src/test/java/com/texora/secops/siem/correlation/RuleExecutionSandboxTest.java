package com.texora.secops.siem.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.siem.alerting.service.AlertLifecycleService;
import com.texora.secops.siem.correlation.engine.CorrelationWindowStore;
import com.texora.secops.siem.correlation.engine.RuleExecutionSandbox;
import com.texora.secops.siem.correlation.service.RuleDefinitionParser;
import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.enums.RuleStatus;
import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.repository.SiemCorrelationRuleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQUIREMENT 2 adversarial test, exactly as the LLD specifies: "deliberately break one rule and
 * assert the others still evaluate." rule-broken has a malformed (non-JSON) definition; rule-ok
 * has a valid single-condition definition matching the incoming event. Both are APPROVED and
 * handed to the same {@code evaluateAll} call.
 */
class RuleExecutionSandboxTest {

    private SiemCorrelationRuleRepository ruleRepository;
    private AlertLifecycleService alertLifecycleService;
    private ExecutorService executor;
    private RuleExecutionSandbox sandbox;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(SiemCorrelationRuleRepository.class);
        alertLifecycleService = mock(AlertLifecycleService.class);
        executor = Executors.newFixedThreadPool(2);
        tenantId = UUID.randomUUID();

        sandbox = new RuleExecutionSandbox(
                ruleRepository,
                new RuleDefinitionParser(new ObjectMapper()),
                new CorrelationWindowStore(),
                alertLifecycleService,
                executor,
                2000L,
                new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void aBrokenRuleDoesNotPreventOtherRulesFromFiring() {
        SiemCorrelationRule brokenRule = new SiemCorrelationRule(UUID.randomUUID(), tenantId, "broken-rule",
                "{ this is not valid json", RuleStatus.APPROVED, UUID.randomUUID(), null, Instant.now(), Instant.now());

        String healthyDefinition = "{"
                + "\"conditions\":[{\"sourceService\":\"ldap\",\"eventType\":\"bind.failed\",\"countThreshold\":1,\"windowSeconds\":60}],"
                + "\"severity\":\"HIGH\"}";
        SiemCorrelationRule healthyRule = new SiemCorrelationRule(UUID.randomUUID(), tenantId, "healthy-rule",
                healthyDefinition, RuleStatus.APPROVED, UUID.randomUUID(), null, Instant.now(), Instant.now());

        when(ruleRepository.findAllByStatus(RuleStatus.APPROVED)).thenReturn(List.of(brokenRule, healthyRule));

        NormalizedEvent event = new NormalizedEvent();
        event.setId(UUID.randomUUID().toString());
        event.setSourceService("ldap");
        event.setEventType("bind.failed");
        event.setTenantScope(tenantId);
        event.setOccurredAt(Instant.now());

        // Must not throw, even though one of the two rules is fundamentally broken.
        sandbox.evaluateAll(event);

        // The healthy rule still fired an alert...
        verify(alertLifecycleService, times(1)).createAlert(eq(healthyRule), eq(event), any());
        // ...and the broken rule never got anywhere near alert creation.
        verify(alertLifecycleService, never()).createAlert(eq(brokenRule), any(), any());
    }
}
