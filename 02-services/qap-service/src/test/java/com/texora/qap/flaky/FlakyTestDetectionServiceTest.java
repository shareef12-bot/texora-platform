package com.texora.qap.flaky;

import com.texora.qap.domain.QapFlakyTestRecord;
import com.texora.qap.repository.QapFlakyTestRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlakyTestDetectionServiceTest {

    @Mock
    private QapFlakyTestRecordRepository flakyRepo;

    private FlakyTestDetectionService service;

    private final UUID testCaseId = UUID.randomUUID();
    private final UUID tenantId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FlakyTestDetectionService(flakyRepo, "0.30");
        ReflectionTestUtils.setField(service, "quarantineThreshold", new BigDecimal("0.30"));
    }

    @Test
    void notQuarantinedWhenNoRecord() {
        when(flakyRepo.findByTestCaseIdAndTenantId(testCaseId, tenantId)).thenReturn(Optional.empty());
        assertThat(service.isQuarantined(testCaseId, tenantId)).isFalse();
    }

    @Test
    void quarantinedWhenFlagSet() {
        QapFlakyTestRecord rec = recordWith(5, 4, true);
        when(flakyRepo.findByTestCaseIdAndTenantId(testCaseId, tenantId)).thenReturn(Optional.of(rec));
        assertThat(service.isQuarantined(testCaseId, tenantId)).isTrue();
    }

    @Test
    void autoQuarantinesWhenFlakeRateExceedsThreshold() {
        QapFlakyTestRecord rec = recordWith(7, 2, false); // 2/9 < 0.30 — will become 3/10=0.30 after fail
        when(flakyRepo.findByTestCaseIdAndTenantId(testCaseId, tenantId)).thenReturn(Optional.of(rec));

        service.recordOutcome(testCaseId, tenantId, false); // fail

        ArgumentCaptor<QapFlakyTestRecord> captor = ArgumentCaptor.forClass(QapFlakyTestRecord.class);
        verify(flakyRepo).save(captor.capture());
        QapFlakyTestRecord saved = captor.getValue();
        assertThat(saved.isQuarantined()).isTrue();
        assertThat(saved.getFlakeRate()).isEqualByComparingTo("0.375");
    }

    @Test
    void doesNotQuarantineBelowThreshold() {
        QapFlakyTestRecord rec = recordWith(10, 1, false); // 1 pass / 10 runs
        when(flakyRepo.findByTestCaseIdAndTenantId(testCaseId, tenantId)).thenReturn(Optional.of(rec));

        service.recordOutcome(testCaseId, tenantId, true); // pass

        ArgumentCaptor<QapFlakyTestRecord> captor = ArgumentCaptor.forClass(QapFlakyTestRecord.class);
        verify(flakyRepo).save(captor.capture());
        assertThat(captor.getValue().isQuarantined()).isFalse();
    }

    @Test
    void createsNewRecordOnFirstRun() {
        when(flakyRepo.findByTestCaseIdAndTenantId(testCaseId, tenantId)).thenReturn(Optional.empty());

        service.recordOutcome(testCaseId, tenantId, true);

        ArgumentCaptor<QapFlakyTestRecord> captor = ArgumentCaptor.forClass(QapFlakyTestRecord.class);
        verify(flakyRepo).save(captor.capture());
        QapFlakyTestRecord saved = captor.getValue();
        assertThat(saved.getRunCount()).isEqualTo(1);
        assertThat(saved.getFailCount()).isEqualTo(0);
    }

    @Test
    void manualQuarantineUpdatesRecord() {
        QapFlakyTestRecord rec = recordWith(5, 0, false);
        when(flakyRepo.findByTestCaseIdAndTenantId(testCaseId, tenantId)).thenReturn(Optional.of(rec));

        service.setQuarantine(testCaseId, tenantId, true);

        ArgumentCaptor<QapFlakyTestRecord> captor = ArgumentCaptor.forClass(QapFlakyTestRecord.class);
        verify(flakyRepo).save(captor.capture());
        assertThat(captor.getValue().isQuarantined()).isTrue();
    }

    // ---- helpers ----

    private QapFlakyTestRecord recordWith(int runCount, int failCount, boolean quarantined) {
        QapFlakyTestRecord r = new QapFlakyTestRecord();
        r.setId(UUID.randomUUID());
        r.setTestCaseId(testCaseId);
        r.setTenantId(tenantId);
        r.setRunCount(runCount);
        r.setFailCount(failCount);
        r.setFlakeRate(runCount == 0
                ? BigDecimal.ZERO
                : new BigDecimal(failCount).divide(new BigDecimal(runCount), 4, java.math.RoundingMode.HALF_UP));
        r.setQuarantined(quarantined);
        r.setUpdatedAt(Instant.now());
        return r;
    }
}
