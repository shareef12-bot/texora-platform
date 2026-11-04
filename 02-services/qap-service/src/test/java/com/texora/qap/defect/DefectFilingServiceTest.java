package com.texora.qap.defect;

import com.texora.qap.defect.adapter.IssueTrackerAdapter;
import com.texora.qap.defect.service.DefectFilingService;
import com.texora.qap.domain.QapDefect;
import com.texora.qap.domain.QapTestResult;
import com.texora.qap.flaky.FlakyTestDetectionService;
import com.texora.qap.repository.QapDefectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefectFilingServiceTest {

    @Mock private QapDefectRepository defectRepo;
    @Mock private IssueTrackerAdapter issueTrackerAdapter;
    @Mock private FlakyTestDetectionService flakyService;

    private DefectFilingService service;

    private final UUID tenantId    = UUID.randomUUID();
    private final UUID testCaseId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DefectFilingService(defectRepo, issueTrackerAdapter, flakyService);
    }

    @Test
    void createsNewDefectOnFirstFailure() {
        QapTestResult result = buildResult("svc", "/api/foo", "NullPointerException");
        when(flakyService.isQuarantined(testCaseId, tenantId)).thenReturn(false);
        when(defectRepo.findByFailureSignatureHashAndTenantId(any(), eq(tenantId))).thenReturn(Optional.empty());
        when(issueTrackerAdapter.createTicket(any())).thenReturn("TICKET-42");
        when(defectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<QapDefect> defect = service.processFailure(result, tenantId);

        assertThat(defect).isPresent();
        assertThat(defect.get().getIssueTrackerRef()).isEqualTo("TICKET-42");
        assertThat(defect.get().getOccurrenceCount()).isEqualTo(1);
        verify(issueTrackerAdapter).createTicket(any());
    }

    @Test
    void incrementsOccurrenceCountOnDuplicateFailure() {
        QapTestResult result = buildResult("svc", "/api/foo", "NullPointerException");
        QapDefect existing = existingDefect("TICKET-42", 3);
        when(flakyService.isQuarantined(testCaseId, tenantId)).thenReturn(false);
        when(defectRepo.findByFailureSignatureHashAndTenantId(any(), eq(tenantId))).thenReturn(Optional.of(existing));
        when(defectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<QapDefect> defect = service.processFailure(result, tenantId);

        assertThat(defect).isPresent();
        assertThat(defect.get().getOccurrenceCount()).isEqualTo(4);
        verify(issueTrackerAdapter, never()).createTicket(any());
        verify(issueTrackerAdapter).addComment(eq("TICKET-42"), any());
    }

    @Test
    void skipsQuarantinedTestCase() {
        QapTestResult result = buildResult("svc", "/api/foo", "RuntimeException");
        when(flakyService.isQuarantined(testCaseId, tenantId)).thenReturn(true);

        Optional<QapDefect> defect = service.processFailure(result, tenantId);

        assertThat(defect).isEmpty();
        verifyNoInteractions(issueTrackerAdapter);
        verifyNoInteractions(defectRepo);
    }

    @Test
    void sameFailureProducesSameHash() {
        String hash1 = service.computeFailureSignatureHash("svc", "/api/foo", "NullPointerException");
        String hash2 = service.computeFailureSignatureHash("svc", "/api/foo", "NullPointerException");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void differentFailureProducesDifferentHash() {
        String hash1 = service.computeFailureSignatureHash("svc", "/api/foo", "NullPointerException");
        String hash2 = service.computeFailureSignatureHash("svc", "/api/bar", "NullPointerException");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    // ---- helpers ----

    private QapTestResult buildResult(String service, String endpoint, String exception) {
        QapTestResult r = new QapTestResult();
        r.setId(UUID.randomUUID());
        r.setTestCaseId(testCaseId);
        r.setTenantId(tenantId);
        r.setOutcome("FAIL");
        r.setCorrelatedService(service);
        r.setCorrelatedEndpoint(endpoint);
        r.setCorrelatedExceptionType(exception);
        r.setExecutedAt(Instant.now());
        return r;
    }

    private QapDefect existingDefect(String ref, int count) {
        QapDefect d = new QapDefect();
        d.setId(UUID.randomUUID());
        d.setTenantId(tenantId);
        d.setTestCaseId(testCaseId);
        d.setIssueTrackerRef(ref);
        d.setOccurrenceCount(count);
        d.setFirstSeenAt(Instant.now().minusSeconds(3600));
        d.setLastSeenAt(Instant.now().minusSeconds(60));
        return d;
    }
}
