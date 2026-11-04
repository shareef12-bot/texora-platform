package com.texora.secops.siem.search;

import com.texora.secops.siem.audit.AuditEventPublisher;
import com.texora.secops.siem.dto.common.PageResponse;
import com.texora.secops.siem.dto.search.NormalizedEventDto;
import com.texora.secops.siem.search.client.EventCorpusClient;
import com.texora.secops.siem.search.client.EventCorpusUnavailableException;
import com.texora.secops.siem.search.service.TenantFilteredSearchService;
import com.texora.secops.siem.security.CrossTenantAccessException;
import com.texora.secops.siem.security.PolicyDecision;
import com.texora.secops.siem.security.PolicyDecisionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQUIREMENT 3, both halves: the adversarial cross-tenant probe (must be 403, never an empty
 *  page) and graceful degradation when the search backend is impaired. */
class TenantFilteredSearchServiceTest {

    private EventCorpusClient eventCorpusClient;
    private PolicyDecisionClient policyDecisionClient;
    private AuditEventPublisher auditEventPublisher;
    private TenantFilteredSearchService service;

    @BeforeEach
    void setUp() {
        eventCorpusClient = mock(EventCorpusClient.class);
        policyDecisionClient = mock(PolicyDecisionClient.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new TenantFilteredSearchService(eventCorpusClient, policyDecisionClient, auditEventPublisher);
    }

    @Test
    void queryingAnotherTenantsScopeIsRejectedNotEmptied() throws Exception {
        UUID callerTenant = UUID.randomUUID();
        UUID someoneElsesTenant = UUID.randomUUID();

        assertThatThrownBy(() -> service.search(callerTenant, UUID.randomUUID(), someoneElsesTenant,
                null, null, null, 0, 50))
                .isInstanceOf(CrossTenantAccessException.class);

        // Must never even reach the backend - this is a reconnaissance attempt, not a query to serve.
        verify(eventCorpusClient, never()).search(any(), anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void ownTenantScopeIsAllowedThrough() throws Exception {
        UUID callerTenant = UUID.randomUUID();
        when(policyDecisionClient.evaluate(any(), anyString(), anyString())).thenReturn(PolicyDecision.allow("ok"));
        when(eventCorpusClient.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new EventCorpusClient.EventCorpusSearchResult(List.of(), 0));

        PageResponse<NormalizedEventDto> result = service.search(callerTenant, UUID.randomUUID(), callerTenant,
                null, null, null, 0, 50);

        assertThat(result.isDegraded()).isFalse();
    }

    @Test
    void backendImpairmentDegradesGracefullyInsteadOfFailingOutright() throws Exception {
        UUID callerTenant = UUID.randomUUID();
        when(policyDecisionClient.evaluate(any(), anyString(), anyString())).thenReturn(PolicyDecision.allow("ok"));
        when(eventCorpusClient.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new EventCorpusUnavailableException("backend down", new RuntimeException("connect timeout")));

        PageResponse<NormalizedEventDto> result = service.search(callerTenant, UUID.randomUUID(), null,
                null, null, null, 0, 50);

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getItems()).isEmpty();
    }
}
