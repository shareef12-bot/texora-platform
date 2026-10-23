package com.texora.qap.integration;

import com.texora.qap.defect.service.DefectFilingService;
import com.texora.qap.domain.QapTestResult;
import com.texora.qap.repository.QapDefectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-based integration test: verifies the full deduplication pipeline
 * — the same failure must NEVER create a second ticket.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DefectFilingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("texora")
            .withUsername("texora")
            .withPassword("texora");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:0/disabled");
    }

    @Autowired private DefectFilingService defectFilingService;
    @Autowired private QapDefectRepository defectRepository;

    @Test
    void sameFailureOnlyCreatesOneDefect() {
        UUID tenantId   = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();

        QapTestResult result = buildResult(tenantId, testCaseId,
                "payment-service", "/api/v1/pay", "TimeoutException");

        // File same failure 3 times
        defectFilingService.processFailure(result, tenantId);
        defectFilingService.processFailure(result, tenantId);
        defectFilingService.processFailure(result, tenantId);

        // Must have exactly ONE defect row
        long count = defectRepository.countByTenantId(tenantId);
        assertThat(count).isEqualTo(1);

        // Occurrence count must be 3
        defectRepository.findByTenantId(tenantId,
                        org.springframework.data.domain.Pageable.unpaged())
                .forEach(d -> assertThat(d.getOccurrenceCount()).isEqualTo(3));
    }

    @Test
    void differentFailuresCreateSeparateDefects() {
        UUID tenantId   = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();

        QapTestResult r1 = buildResult(tenantId, testCaseId,
                "svc-a", "/api/foo", "NullPointerException");
        QapTestResult r2 = buildResult(tenantId, testCaseId,
                "svc-b", "/api/bar", "IllegalArgumentException");

        defectFilingService.processFailure(r1, tenantId);
        defectFilingService.processFailure(r2, tenantId);

        long count = defectRepository.countByTenantId(tenantId);
        assertThat(count).isEqualTo(2);
    }

    private QapTestResult buildResult(UUID tenantId, UUID testCaseId,
                                       String service, String endpoint, String exception) {
        QapTestResult r = new QapTestResult();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setTestCaseId(testCaseId);
        r.setOutcome("FAIL");
        r.setCorrelatedService(service);
        r.setCorrelatedEndpoint(endpoint);
        r.setCorrelatedExceptionType(exception);
        r.setExecutedAt(Instant.now());
        return r;
    }
}
