package com.texora.secops.ldap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.ldap.schema.dto.AttributeRequest;
import com.texora.secops.ldap.schema.dto.SchemaDefinitionRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker-based integration tests for ldap-service.
 *
 * <p>Class name ends in {@code IntegrationTest} so maven-failsafe-plugin picks it up
 * and maven-surefire-plugin excludes it from unit test runs.
 *
 * <p>Containers used:
 * <ul>
 *   <li>PostgreSQL 16 — real Flyway migrations applied</li>
 *   <li>Apache Kafka (Confluent) — real broker for dc.sync.requested / ldap.audit.events</li>
 * </ul>
 *
 * <p>Auth is bypassed via the {@code integration-test} Spring profile which replaces
 * {@link com.texora.secops.ldap.security.RbacInterceptor} with a pass-through stub.
 * No real IAM token is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("LdapService — Integration")
class LdapServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ldap_test")
            .withUsername("ldap_svc")
            .withPassword("ldap_svc_pass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Use stub engine in integration tests — no real LDAP server
        registry.add("ldap.engine.host", () -> "STUB");
    }

    @BeforeAll
    static void createTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic("dc.sync.requested", 1, (short) 1),
                    new NewTopic("ldap.audit.events", 1, (short) 1)
            )).all().get();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Health / Actuator
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /actuator/health → 200 UP")
    void actuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // -------------------------------------------------------------------------
    // Schema definition workflow
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/schema/attribute-management → 201 DRAFT")
    void submitSchema_createsDraft() throws Exception {
        SchemaDefinitionRequest req = new SchemaDefinitionRequest();
        req.setObjectClass("texoraDeviceIT");
        AttributeRequest attr = new AttributeRequest();
        attr.setName("serialNumber");
        attr.setSyntax("1.3.6.1.4.1.1466.115.121.1.26");
        attr.setRequired(true);
        req.setAttributes(List.of(attr));

        mockMvc.perform(post("/api/v1/schema/attribute-management")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-Integration-Actor", UUID.randomUUID().toString())
                        .header("X-Integration-Tenant", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectClass").value("texoraDeviceIT"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /api/v1/schema/attribute-management with invalid objectClass → 422")
    void submitSchema_invalidObjectClass_returns422() throws Exception {
        SchemaDefinitionRequest req = new SchemaDefinitionRequest();
        req.setObjectClass("bad name!"); // spaces and ! are invalid
        req.setAttributes(List.of());

        mockMvc.perform(post("/api/v1/schema/attribute-management")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-Integration-Actor", UUID.randomUUID().toString())
                        .header("X-Integration-Tenant", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // Bind config
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/secure-bind-configuration with tlsRequired=false → 422")
    void createBindConfig_noTls_returns422() throws Exception {
        Map<String, Object> req = Map.of(
                "bindDn", "cn=svc,dc=example,dc=com",
                "tlsRequired", false
        );

        mockMvc.perform(post("/api/v1/secure-bind-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-Integration-Actor", UUID.randomUUID().toString())
                        .header("X-Integration-Tenant", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /api/v1/secure-bind-configuration → 200 empty list for new tenant")
    void listBindConfigs_emptyForNewTenant() throws Exception {
        mockMvc.perform(get("/api/v1/secure-bind-configuration")
                        .header("X-Integration-Actor", UUID.randomUUID().toString())
                        .header("X-Integration-Tenant", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // -------------------------------------------------------------------------
    // Audit events
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/audit/events → 200 paged response")
    void listAuditEvents_returnsPage() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events")
                        .header("X-Integration-Actor", UUID.randomUUID().toString())
                        .header("X-Integration-Tenant", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // -------------------------------------------------------------------------
    // OpenAPI
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /v3/api-docs → 200 with service title")
    void openApiDocs_available() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Texora SecOps — LDAP Directory Service"));
    }
}
