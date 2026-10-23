package com.texora.qap.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Docker-based integration test for the TestSuite REST API.
 * Requires Docker on the CI runner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TestSuiteControllerIntegrationTest {

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
        // Disable security for integration test
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:0/disabled");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createSuiteReturns201() throws Exception {
        String body = """
                {
                  "name": "Smoke Suite",
                  "targetSystem": "iam-service",
                  "suiteType": "API",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(post("/api/v1/suites")
                        .header("X-Tenant-ID", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Smoke Suite"))
                .andExpect(jsonPath("$.suiteType").value("API"));
    }

    @Test
    void listSuitesReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/suites")
                        .header("X-Tenant-ID", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getMissingSuiteReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/suites/00000000-0000-0000-0000-000000000099")
                        .header("X-Tenant-ID", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSuiteValidationFailsOnInvalidType() throws Exception {
        String body = """
                {
                  "name": "Bad Suite",
                  "targetSystem": "some-svc",
                  "suiteType": "INVALID"
                }
                """;

        mockMvc.perform(post("/api/v1/suites")
                        .header("X-Tenant-ID", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
