package com.texora.qap.suite.service;

import com.texora.qap.domain.QapTestCase;
import com.texora.qap.domain.QapTestSuite;
import com.texora.qap.dto.CreateSuiteRequest;
import com.texora.qap.dto.CreateTestCaseRequest;
import com.texora.qap.exception.SuiteNotFoundException;
import com.texora.qap.repository.QapTestCaseRepository;
import com.texora.qap.repository.QapTestSuiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages test suites and their test cases. All operations are tenant-scoped.
 */
@Service
public class TestSuiteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSuiteService.class);

    private final QapTestSuiteRepository suiteRepository;
    private final QapTestCaseRepository testCaseRepository;

    public TestSuiteService(QapTestSuiteRepository suiteRepository,
                             QapTestCaseRepository testCaseRepository) {
        this.suiteRepository = suiteRepository;
        this.testCaseRepository = testCaseRepository;
    }

    // ---- Suite management -----------------------------------------------

    public Page<QapTestSuite> listSuites(UUID tenantId, Pageable pageable) {
        return suiteRepository.findByTenantId(tenantId, pageable);
    }

    public List<QapTestSuite> listSuites(UUID tenantId) {
        return suiteRepository.findAllByTenantId(tenantId);
    }

    public QapTestSuite getSuite(UUID suiteId, UUID tenantId) {
        return suiteRepository.findByIdAndTenantId(suiteId, tenantId)
                .orElseThrow(() -> new SuiteNotFoundException(
                        "Test suite " + suiteId + " not found for tenant " + tenantId));
    }

    public Optional<QapTestSuite> findSuite(UUID suiteId, UUID tenantId) {
        return suiteRepository.findByIdAndTenantId(suiteId, tenantId);
    }

    @Transactional
    public QapTestSuite createSuite(UUID tenantId, CreateSuiteRequest request) {
        return createSuite(tenantId, request.getName(), request.getTargetSystem(),
                request.getSuiteType(), request.getStatus());
    }

    @Transactional
    public QapTestSuite createSuite(UUID tenantId, String name, String targetSystem,
                                    String suiteType, String status) {
        Instant now = Instant.now();
        QapTestSuite suite = new QapTestSuite(
                UUID.randomUUID(), tenantId, name, targetSystem,
                suiteType, status != null ? status : "ACTIVE", now, now
        );
        LOGGER.info("Creating test suite name='{}' type={} for tenant={}", name, suiteType, tenantId);
        return suiteRepository.save(suite);
    }

    @Transactional
    public void deleteSuite(UUID suiteId, UUID tenantId) {
        QapTestSuite suite = getSuite(suiteId, tenantId);
        suiteRepository.delete(suite);
        LOGGER.info("Deleted test suite {} for tenant={}", suiteId, tenantId);
    }

    @Transactional
    public QapTestSuite updateSuiteStatus(UUID suiteId, UUID tenantId, String status) {
        QapTestSuite suite = getSuite(suiteId, tenantId);
        suite.setStatus(status);
        suite.setUpdatedAt(Instant.now());
        return suiteRepository.save(suite);
    }

    // ---- Test case management -------------------------------------------

    public Page<QapTestCase> listTestCases(UUID suiteId, UUID tenantId, Pageable pageable) {
        getSuite(suiteId, tenantId); // assert ownership
        return testCaseRepository.findBySuiteId(suiteId, pageable);
    }

    public List<QapTestCase> listCases(UUID suiteId, UUID tenantId) {
        getSuite(suiteId, tenantId);
        return testCaseRepository.findAllBySuiteId(suiteId);
    }

    @Transactional
    public QapTestCase addTestCase(UUID suiteId, UUID tenantId, CreateTestCaseRequest request) {
        return addCase(suiteId, tenantId, request.getName(),
                request.getSteps(), request.getExpectedResult());
    }

    @Transactional
    public QapTestCase addCase(UUID suiteId, UUID tenantId, String name,
                                String steps, String expectedResult) {
        getSuite(suiteId, tenantId);
        QapTestCase tc = new QapTestCase(
                UUID.randomUUID(), suiteId, name,
                steps != null ? steps : "[]",
                expectedResult,
                Instant.now()
        );
        tc.setTenantId(tenantId);
        LOGGER.info("Adding test case '{}' to suite {}", name, suiteId);
        return testCaseRepository.save(tc);
    }

    @Transactional
    public void deleteTestCase(UUID suiteId, UUID caseId, UUID tenantId) {
        getSuite(suiteId, tenantId); // assert ownership
        testCaseRepository.findByIdAndSuiteId(caseId, suiteId)
                .ifPresent(testCaseRepository::delete);
    }
}
