package com.texora.qap.integration;

import com.texora.qap.domain.QapTestRun;
import com.texora.qap.execution.adapter.ApiTestRunnerAdapter;
import com.texora.qap.execution.service.TestRunOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A brand-new test case that fails in a real run must file exactly one defect.
 * No flaky history is prepared on purpose.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class FirstFailureFilesDefectIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("texora")
            .withUsername("texora")
            .withPassword("texora");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withStartupTimeout(java.time.Duration.ofMinutes(5));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("texora.iam.jwks-uri", () -> "http://localhost:1/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:0/disabled");
    }

    @Autowired private TestRunOrchestrationService orchestrationService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private ApiTestRunnerAdapter apiRunner;

    @Test
    void firstFailureOfNewTestCaseFilesOneDefect() {
        UUID tenantId = UUID.randomUUID();
        UUID suiteId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        jdbc.update("insert into qap.qap_test_suite (id, tenant_id, name, target_system, suite_type, status) values (?, ?, 'it-suite', 'it-system', 'API', 'ACTIVE')", suiteId, tenantId);
        jdbc.update("insert into qap.qap_test_case (id, suite_id, tenant_id, name) values (?, ?, ?, 'it-case')", caseId, suiteId, tenantId);

        when(apiRunner.execute(any(), any(), any())).thenReturn(
                new ApiTestRunnerAdapter.ApiTestResult(false, null, 5L, null, null, 500, "boom"));

        QapTestRun run = orchestrationService.triggerRun(suiteId, tenantId, "MANUAL");

        assertThat(run.getStatus()).isEqualTo("FAILED");
        Long defects = jdbc.queryForObject(
                "select count(*) from qap.qap_defect where tenant_id = ?", Long.class, tenantId);
        assertThat(defects).isEqualTo(1L);
    }
}