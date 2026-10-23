package com.texora.secops.siem.correlation.engine;

import com.texora.secops.siem.alerting.service.AlertLifecycleService;
import com.texora.secops.siem.correlation.model.RuleCondition;
import com.texora.secops.siem.correlation.model.RuleDefinition;
import com.texora.secops.siem.correlation.service.RuleDefinitionParser;
import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.enums.RuleStatus;
import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.repository.SiemCorrelationRuleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * REQUIREMENT 2 - RULE ISOLATION. Every APPROVED rule is evaluated in its own bounded task: a
 * malformed definition, a runtime exception, or a rule that runs too long is caught/cancelled
 * and logged without affecting any other rule's evaluation or the ingestion pipeline itself.
 * {@code RuleExecutionSandboxTest} deliberately breaks one rule and asserts the others still
 * fire, per the LLD's own adversarial-testing instruction.
 */
@Component
public class RuleExecutionSandbox {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleExecutionSandbox.class);

    private final SiemCorrelationRuleRepository ruleRepository;
    private final RuleDefinitionParser ruleDefinitionParser;
    private final CorrelationWindowStore windowStore;
    private final AlertLifecycleService alertLifecycleService;
    private final ExecutorService ruleExecutionExecutor;
    private final long ruleTimeoutMs;
    private final Counter ruleFailureCounter;

    public RuleExecutionSandbox(SiemCorrelationRuleRepository ruleRepository,
                                 RuleDefinitionParser ruleDefinitionParser,
                                 CorrelationWindowStore windowStore,
                                 AlertLifecycleService alertLifecycleService,
                                 ExecutorService ruleExecutionExecutor,
                                 @Value("${siem.correlation.rule-timeout-ms:2000}") long ruleTimeoutMs,
                                 MeterRegistry meterRegistry) {
        this.ruleRepository = ruleRepository;
        this.ruleDefinitionParser = ruleDefinitionParser;
        this.windowStore = windowStore;
        this.alertLifecycleService = alertLifecycleService;
        this.ruleExecutionExecutor = ruleExecutionExecutor;
        this.ruleTimeoutMs = ruleTimeoutMs;
        this.ruleFailureCounter = Counter.builder("siem_correlation_rule_failures_total")
                .description("Rule evaluations that threw, timed out, or failed to parse - isolated per rule")
                .register(meterRegistry);
    }

    /** Evaluates every APPROVED rule against one event. Never throws - a total failure of an
     *  individual rule is swallowed (after logging/metrics) so the caller (the ingestion
     *  pipeline) is never destabilised by a bad rule. */
    public void evaluateAll(NormalizedEvent event) {
        List<SiemCorrelationRule> approvedRules = ruleRepository.findAllByStatus(RuleStatus.APPROVED);
        for (SiemCorrelationRule rule : approvedRules) {
            evaluateOneIsolated(rule, event);
        }
    }

    private void evaluateOneIsolated(SiemCorrelationRule rule, NormalizedEvent event) {
        Future<Void> future = ruleExecutionExecutor.submit((Callable<Void>) () -> {
            evaluate(rule, event);
            return null;
        });
        try {
            future.get(ruleTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            ruleFailureCounter.increment();
            LOGGER.error("Correlation rule {} timed out after {}ms - isolated, other rules unaffected",
                    rule.getId(), ruleTimeoutMs);
        } catch (Exception ex) {
            ruleFailureCounter.increment();
            LOGGER.error("Correlation rule {} threw during evaluation - isolated, other rules unaffected",
                    rule.getId(), ex.getCause() != null ? ex.getCause() : ex);
        }
    }

    private void evaluate(SiemCorrelationRule rule, NormalizedEvent event) {
        // Only tenant-scoped rules matching the event's tenant are eligible - a rule never
        // correlates across tenants (defense in depth alongside B.5's repository-level scoping).
        if (!Objects.equals(rule.getTenantId(), event.getTenantScope())) {
            return;
        }

        RuleDefinition definition = ruleDefinitionParser.parse(rule.getRuleDefinition());
        List<RuleCondition> conditions = definition.getConditions();
        RuleCondition stage0 = conditions.get(0);
        Instant now = Instant.now();

        if (matches(stage0, event)) {
            String stage0Key = rule.getId() + ":" + event.getTenantScope() + ":stage0";
            int windowSeconds = stage0.getWindowSeconds() == null ? 60 : stage0.getWindowSeconds();
            int threshold = stage0.getCountThreshold() == null ? 1 : stage0.getCountThreshold();
            int count = windowStore.recordAndCount(stage0Key, now, windowSeconds);

            if (count >= threshold) {
                if (conditions.size() == 1) {
                    fire(rule, event, definition);
                    return;
                }
                windowStore.markStageComplete(rule.getId() + ":" + event.getTenantScope(), now);
            }
        }

        if (conditions.size() > 1 && matches(conditions.get(1), event)) {
            RuleCondition stage1 = conditions.get(1);
            String pendingKey = rule.getId() + ":" + event.getTenantScope();
            Instant stage0CompletedAt = windowStore.getStageCompletion(pendingKey);
            int withinSeconds = stage1.getWithinSeconds() == null ? 300 : stage1.getWithinSeconds();

            if (stage0CompletedAt != null && !now.isAfter(stage0CompletedAt.plusSeconds(withinSeconds))) {
                windowStore.clearStageCompletion(pendingKey);
                fire(rule, event, definition);
            }
        }
    }

    private boolean matches(RuleCondition condition, NormalizedEvent event) {
        return Objects.equals(condition.getSourceService(), event.getSourceService())
                && Objects.equals(condition.getEventType(), event.getEventType());
    }

    private void fire(SiemCorrelationRule rule, NormalizedEvent event, RuleDefinition definition) {
        alertLifecycleService.createAlert(rule, event, definition.getSeverity());
    }
}
