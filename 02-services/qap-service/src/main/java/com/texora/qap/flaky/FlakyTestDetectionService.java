package com.texora.qap.flaky;

import com.texora.qap.domain.QapFlakyTestRecord;
import com.texora.qap.repository.QapFlakyTestRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks per-test-case failure rates and quarantines tests that are flaky beyond
 * a configured threshold.  A quarantined test still has its result recorded but
 * does NOT trigger defect filing — it should not spam the issue tracker with
 * tickets that reflect test instability rather than genuine bugs.
 */
@Service
public class FlakyTestDetectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlakyTestDetectionService.class);

    /** Default flake-rate threshold above which a test is quarantined (LLD §12 open item). */
    private static final BigDecimal DEFAULT_QUARANTINE_THRESHOLD = new BigDecimal("0.30");

    private final QapFlakyTestRecordRepository flakyTestRecordRepository;
    private final BigDecimal quarantineThreshold;

    public FlakyTestDetectionService(
            QapFlakyTestRecordRepository flakyTestRecordRepository,
            @Value("${qap.flaky.quarantine-threshold:0.30}") String quarantineThresholdStr) {
        this.flakyTestRecordRepository = flakyTestRecordRepository;
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(quarantineThresholdStr);
        } catch (NumberFormatException ex) {
            LOGGER.warn("Invalid qap.flaky.quarantine-threshold value '{}'; using default {}",
                    quarantineThresholdStr, DEFAULT_QUARANTINE_THRESHOLD);
            parsed = DEFAULT_QUARANTINE_THRESHOLD;
        }
        this.quarantineThreshold = parsed;
    }

    /**
     * Check whether a test case is currently quarantined.
     * Called BEFORE defect filing; if quarantined, no ticket is created.
     *
     * @param testCaseId the test case
     * @param tenantId   the tenant
     * @return true if the test case is quarantined
     */
    public boolean isQuarantined(UUID testCaseId, UUID tenantId) {
        return flakyTestRecordRepository
                .findByTestCaseIdAndTenantId(testCaseId, tenantId)
                .map(QapFlakyTestRecord::isQuarantined)
                .orElse(false);
    }

    /**
     * Record the outcome of a single test execution and update flake_rate.
     * Creates the record if this is the first run for the test case.
     *
     * @param testCaseId the test case
     * @param tenantId   the tenant
     * @param passed     whether this particular execution passed
     */
    @Transactional
    public void recordOutcome(UUID testCaseId, UUID tenantId, boolean passed) {
        Optional<QapFlakyTestRecord> existing =
                flakyTestRecordRepository.findByTestCaseIdAndTenantId(testCaseId, tenantId);

        QapFlakyTestRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.setRunCount(record.getRunCount() + 1);
            if (!passed) {
                record.setFailCount(record.getFailCount() + 1);
            }
        } else {
            record = new QapFlakyTestRecord(
                    UUID.randomUUID(), testCaseId, tenantId,
                    1, passed ? 0 : 1,
                    BigDecimal.ZERO, false, Instant.now()
            );
        }

        // flake_rate = failCount / runCount
        BigDecimal rate = record.getRunCount() > 0
                ? BigDecimal.valueOf(record.getFailCount())
                        .divide(BigDecimal.valueOf(record.getRunCount()), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        record.setFlakeRate(rate);
        record.setUpdatedAt(Instant.now());

        // Auto-quarantine if threshold exceeded
        if (!record.isQuarantined() && rate.compareTo(quarantineThreshold) > 0) {
            record.setQuarantined(true);
            record.setQuarantinedAt(Instant.now());
            LOGGER.warn("Auto-quarantined test case {} (tenant={}) with flake_rate={}",
                    testCaseId, tenantId, rate);
        }

        flakyTestRecordRepository.save(record);
    }

    /**
     * Manually quarantine or un-quarantine a test case (admin action, RBAC-gated).
     *
     * @param testCaseId  the test case
     * @param tenantId    the tenant
     * @param quarantine  true to quarantine, false to lift quarantine
     */
    @Transactional
    public QapFlakyTestRecord setQuarantine(UUID testCaseId, UUID tenantId, boolean quarantine) {
        QapFlakyTestRecord record = flakyTestRecordRepository
                .findByTestCaseIdAndTenantId(testCaseId, tenantId)
                .orElseGet(() -> new QapFlakyTestRecord(
                        UUID.randomUUID(), testCaseId, tenantId, 0, 0,
                        BigDecimal.ZERO, false, Instant.now()));

        record.setQuarantined(quarantine);
        record.setQuarantinedAt(quarantine ? Instant.now() : null);
        record.setUpdatedAt(Instant.now());

        LOGGER.info("{} quarantine for test case {} (tenant={})",
                quarantine ? "Setting" : "Lifting", testCaseId, tenantId);

        return flakyTestRecordRepository.save(record);
    }

    /**
     * Get the flaky test record for a test case, or empty if never tracked.
     */
    public Optional<QapFlakyTestRecord> getRecord(UUID testCaseId, UUID tenantId) {
        return flakyTestRecordRepository.findByTestCaseIdAndTenantId(testCaseId, tenantId);
    }

    /**
     * List all flaky records for a tenant.
     */
    public List<QapFlakyTestRecord> listByTenant(UUID tenantId) {
        return flakyTestRecordRepository.findAllByTenantId(tenantId);
    }

    public BigDecimal getQuarantineThreshold() {
        return quarantineThreshold;
    }
}
