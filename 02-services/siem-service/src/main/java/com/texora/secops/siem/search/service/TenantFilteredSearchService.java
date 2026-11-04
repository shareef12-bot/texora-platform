package com.texora.secops.siem.search.service;

import com.texora.secops.siem.audit.AuditEventPublisher;
import com.texora.secops.siem.domain.enums.AuditOutcome;
import com.texora.secops.siem.dto.common.PageResponse;
import com.texora.secops.siem.dto.search.NormalizedEventDto;
import com.texora.secops.siem.search.client.EventCorpusClient;
import com.texora.secops.siem.search.client.EventCorpusUnavailableException;
import com.texora.secops.siem.security.CrossTenantAccessException;
import com.texora.secops.siem.security.PolicyDecision;
import com.texora.secops.siem.security.PolicyDecisionClient;
import com.texora.secops.siem.security.PolicyDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * REQUIREMENT 3 - TENANT/RBAC-SCOPED SEARCH. Two independent controls, both mandatory:
 * <ol>
 *   <li>Every query is pinned to the caller's own tenant. If the caller supplies a
 *       {@code tenantScope} that does not match their own token-derived tenant, this throws
 *       {@link CrossTenantAccessException} (403) - it never silently substitutes the caller's
 *       tenant or returns an empty result, either of which would let a caller distinguish
 *       "wrong tenant" from "no data" and use SIEM itself for reconnaissance.</li>
 *   <li>A live policy check against SEC gates the query on top of the static RBAC role already
 *       enforced by RbacInterceptor - fail closed on any SEC failure (B.6.3), exactly like every
 *       other policy decision in this service.</li>
 * </ol>
 * Backend impairment is handled separately from both of the above: an
 * {@link EventCorpusUnavailableException} degrades to an empty, explicitly-flagged result
 * instead of a hard failure (non-functional requirement), because "the search backend is down"
 * is an availability concern, not a security one, and must not be conflated with a denial.
 */
@Service
public class TenantFilteredSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantFilteredSearchService.class);

    private final EventCorpusClient eventCorpusClient;
    private final PolicyDecisionClient policyDecisionClient;
    private final AuditEventPublisher auditEventPublisher;

    public TenantFilteredSearchService(EventCorpusClient eventCorpusClient,
                                        PolicyDecisionClient policyDecisionClient,
                                        AuditEventPublisher auditEventPublisher) {
        this.eventCorpusClient = eventCorpusClient;
        this.policyDecisionClient = policyDecisionClient;
        this.auditEventPublisher = auditEventPublisher;
    }

    public PageResponse<NormalizedEventDto> search(UUID callerTenantId, UUID actorId, UUID requestedTenantScope,
                                                     String eventType, String sourceService, String productId,
                                                     int page, int pageSize) {
        if (requestedTenantScope != null && !requestedTenantScope.equals(callerTenantId)) {
            // The adversarial case Requirement 3 calls out explicitly: 403, not an empty page.
            throw new CrossTenantAccessException(
                    "Caller's tenant does not match the requested tenantScope " + requestedTenantScope);
        }
        UUID scope = callerTenantId;

        PolicyDecision decision = policyDecisionClient.evaluate(callerTenantId, "search.events", scope.toString());
        auditEventPublisher.publish(callerTenantId, actorId, "search.policy-check", "normalized_event",
                scope.toString(), decision.isAllowed() ? AuditOutcome.SUCCESS : AuditOutcome.DENIED, decision.getReason());
        if (!decision.isAllowed()) {
            throw new PolicyDeniedException(decision.getReason());
        }

        try {
            EventCorpusClient.EventCorpusSearchResult result = eventCorpusClient.search(
                    scope, eventType, sourceService, productId, page, pageSize);
            List<NormalizedEventDto> items = result.getEvents().stream().map(NormalizedEventDto::from).toList();
            return PageResponse.of(items, page, pageSize, result.getTotalHits());
        } catch (EventCorpusUnavailableException ex) {
            LOGGER.error("Event corpus search backend impaired - degrading gracefully with an empty result", ex);
            PageResponse<NormalizedEventDto> degraded = PageResponse.of(List.of(), page, pageSize, 0);
            degraded.setDegraded(true);
            return degraded;
        }
    }
}
