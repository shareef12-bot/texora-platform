package com.texora.secops.ftp.api;

import com.jayway.jsonpath.JsonPath;
import com.texora.secops.ftp.dto.PolicySummary;
import com.texora.secops.ftp.quarantine.client.ContentInspectionClient;
import com.texora.secops.ftp.security.PolicyDecision;
import com.texora.secops.ftp.security.PolicyDecisionClient;
import com.texora.secops.ftp.security.TransferCredentialClient;
import com.texora.secops.ftp.transfer.client.SecureTransferEngineAdapter;
import com.texora.secops.ftp.transfer.client.TransferTransportException;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack API tests (real Spring context, real PostgreSQL + Kafka via Testcontainers; needs Docker).
 * Token validation and the four outbound collaborators are mocked; everything else (RBAC interceptor, services,
 * JPA, Flyway, audit) is real. Covers the 401 / 403 / 409 / 422 paths and the two-person quarantine rule end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransferApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws Exception {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.flyway.user", pg::getUsername);
        r.add("spring.flyway.password", pg::getPassword);
        r.add("spring.flyway.placeholders.app_role", pg::getUsername);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("ftp.staging.directory", () -> {
            try {
                return Files.createTempDirectory("ftp-it-staging").toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        r.add("ftp.transfer.retry-backoff-ms", () -> "0");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    TokenValidator tokenValidator;
    @MockitoBean
    PolicyDecisionClient policyClient;
    @MockitoBean
    TransferCredentialClient credentialClient;
    @MockitoBean
    ContentInspectionClient inspectionClient;
    @MockitoBean
    SecureTransferEngineAdapter adapter;

    private static final String FINGERPRINT = "SHA256:" + "A".repeat(43);

    // ------------------------------------------------------------------ helpers

    private String token(UUID tenant, UUID subject, String... roles) {
        String bearer = "Bearer " + UUID.randomUUID();
        TokenClaims claims = mock(TokenClaims.class);
        when(claims.getTenantId()).thenReturn(tenant);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.getRoles()).thenReturn(Set.of(roles));
        when(claims.hasRole(anyString())).thenAnswer(inv -> Set.of(roles).contains(inv.<String>getArgument(0)));
        when(tokenValidator.validate(bearer)).thenReturn(claims);
        return bearer;
    }

    private ResultActions json(MockHttpServletRequestBuilder b, String bearer, String body) throws Exception {
        MockHttpServletRequestBuilder req = b.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private UUID seedEndpoint(UUID tenant, String direction, boolean allowlisted) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ftp.ftp_endpoint (id, tenant_id, host, port, direction, protocol, base_path, credential_ref, "
                        + "host_key_fingerprint, allowlisted, added_by, added_at) VALUES (?, ?, ?, 22, ?, 'SFTP', '/', 'ref', ?, ?, ?, ?)",
                id, tenant, "h-" + id + ".example.com", direction, FINGERPRINT, allowlisted, UUID.randomUUID(),
                Timestamp.from(Instant.now()));
        return id;
    }

    private UUID seedJob(UUID tenant, UUID initiator, String status) {
        UUID src = seedEndpoint(tenant, "SOURCE", true);
        UUID dst = seedEndpoint(tenant, "DEST", true);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ftp.ftp_transfer_job (id, tenant_id, initiator_sso_subject, source_endpoint_id, dest_endpoint_id, "
                        + "file_name, require_inspection, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'payroll.csv', true, ?, ?, ?)",
                id, tenant, initiator, src, dst, status, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return id;
    }

    private UUID seedQuarantine(UUID tenant, UUID jobId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ftp.ftp_quarantine_record (id, tenant_id, transfer_job_id, reason, quarantined_at, status) "
                + "VALUES (?, ?, ?, 'MALICIOUS_CONTENT', ?, 'QUARANTINED')", id, tenant, jobId, Timestamp.from(Instant.now()));
        return id;
    }

    // ------------------------------------------------------------------ authentication / RBAC

    @Test
    void noTokenIs401WithTheStandardErrorBody() throws Exception {
        mvc.perform(get("/api/v1/transfer-jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void validTokenWithoutTheRoleIs403() throws Exception {
        String t = token(UUID.randomUUID(), UUID.randomUUID(), "ftp.audit.read");
        json(get("/api/v1/transfer-jobs"), t, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RBAC_DENIED"));
    }

    @Test
    void readOnlyRoleCannotSubmitJobs() throws Exception {
        String t = token(UUID.randomUUID(), UUID.randomUUID(), "ftp.job.read");
        json(post("/api/v1/transfer-jobs"), t, "{}").andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ Requirement 1: allowlisting

    @Test
    void submittingToANonAllowlistedEndpointIs422AndCreatesNoJob() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID src = seedEndpoint(tenant, "SOURCE", false);          // NOT allowlisted
        UUID dst = seedEndpoint(tenant, "DEST", true);
        String t = token(tenant, UUID.randomUUID(), "ftp.job.write");
        when(policyClient.decide(any(), any(), anyString(), anyString())).thenReturn(new PolicyDecision(true, false, "ALLOW"));

        json(post("/api/v1/transfer-jobs"), t,
                "{\"sourceEndpointId\":\"" + src + "\",\"destEndpointId\":\"" + dst + "\",\"fileName\":\"payroll.csv\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_ALLOWLISTED"));

        Integer jobs = jdbc.queryForObject("SELECT count(*) FROM ftp.ftp_transfer_job WHERE tenant_id = ?", Integer.class, tenant);
        org.assertj.core.api.Assertions.assertThat(jobs).isZero();
    }

    @Test
    void registeredEndpointCanBeListedAndSftpRequiresAPinnedHostKey() throws Exception {
        UUID tenant = UUID.randomUUID();
        String t = token(tenant, UUID.randomUUID(), "ftp.endpoint.write", "ftp.endpoint.read");

        json(post("/api/v1/endpoints"), t, "{\"host\":\"partner.example.com\",\"port\":22,\"direction\":\"SOURCE\","
                + "\"protocol\":\"SFTP\",\"credentialRef\":\"sec://ref\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENDPOINT_HOST_KEY_REQUIRED"));

        json(post("/api/v1/endpoints"), t, "{\"host\":\"partner.example.com\",\"port\":22,\"direction\":\"SOURCE\","
                + "\"protocol\":\"SFTP\",\"credentialRef\":\"sec://ref\",\"hostKeyFingerprint\":\"" + FINGERPRINT + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allowlisted").value(true));

        json(get("/api/v1/endpoints"), t, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void internalAddressesCannotBeRegistered() throws Exception {
        String t = token(UUID.randomUUID(), UUID.randomUUID(), "ftp.endpoint.write");
        for (String host : new String[]{"169.254.169.254", "127.0.0.1", "localhost"}) {
            json(post("/api/v1/endpoints"), t, "{\"host\":\"" + host + "\",\"port\":22,\"direction\":\"SOURCE\","
                    + "\"protocol\":\"SFTP\",\"credentialRef\":\"r\",\"hostKeyFingerprint\":\"" + FINGERPRINT + "\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ENDPOINT_HOST_FORBIDDEN"));
        }
    }

    // ------------------------------------------------------------------ happy path + validation + tenancy

    @Test
    void validSubmissionIs201AndTheJobIsReadable() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID src = seedEndpoint(tenant, "SOURCE", true);
        UUID dst = seedEndpoint(tenant, "DEST", true);
        String t = token(tenant, UUID.randomUUID(), "ftp.job.write", "ftp.job.read");
        when(policyClient.decide(any(), any(), anyString(), anyString())).thenReturn(new PolicyDecision(true, false, "ALLOW"));
        when(adapter.fetchToStaging(any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(TransferTransportException.fatal("TEST_STOP", "stop", null));

        String body = json(post("/api/v1/transfer-jobs"), t,
                "{\"sourceEndpointId\":\"" + src + "\",\"destEndpointId\":\"" + dst + "\",\"fileName\":\"payroll.csv\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(body, "$.jobId");

        json(get("/api/v1/transfer-jobs/" + jobId), t, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.fileName").value("payroll.csv"));
    }

    @Test
    void policyServiceOutageStopsTheTransferFailClosedWith504() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID src = seedEndpoint(tenant, "SOURCE", true);
        UUID dst = seedEndpoint(tenant, "DEST", true);
        String t = token(tenant, UUID.randomUUID(), "ftp.job.write");
        when(policyClient.decide(any(), any(), anyString(), anyString())).thenReturn(new PolicyDecision(false, true, "POLICY_SERVICE_UNAVAILABLE"));

        json(post("/api/v1/transfer-jobs"), t,
                "{\"sourceEndpointId\":\"" + src + "\",\"destEndpointId\":\"" + dst + "\",\"fileName\":\"payroll.csv\"}")
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("POLICY_UNAVAILABLE"));
    }

    @Test
    void invalidBodiesAre422AndMalformedJsonIs400() throws Exception {
        String t = token(UUID.randomUUID(), UUID.randomUUID(), "ftp.job.write");
        json(post("/api/v1/transfer-jobs"), t, "{\"sourceEndpointId\":\"" + UUID.randomUUID() + "\",\"destEndpointId\":\""
                + UUID.randomUUID() + "\",\"fileName\":\"../etc/passwd\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        json(post("/api/v1/transfer-jobs"), t, "{not json").andExpect(status().isBadRequest());
    }

    @Test
    void anotherTenantsJobIs403NotEmptyAndMissingIs404() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID jobA = seedJob(tenantA, UUID.randomUUID(), "COMPLETED");
        String asA = token(tenantA, UUID.randomUUID(), "ftp.job.read");
        String asB = token(UUID.randomUUID(), UUID.randomUUID(), "ftp.job.read");

        json(get("/api/v1/transfer-jobs/" + jobA), asA, null).andExpect(status().isOk());
        json(get("/api/v1/transfer-jobs/" + jobA), asB, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
        json(get("/api/v1/transfer-jobs/" + UUID.randomUUID()), asB, null).andExpect(status().isNotFound());
    }

    @Test
    void cancellingAFinishedJobIs409() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID job = seedJob(tenant, UUID.randomUUID(), "COMPLETED");
        String t = token(tenant, UUID.randomUUID(), "ftp.job.write");
        json(post("/api/v1/transfer-jobs/" + job + "/cancel"), t, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_NOT_CANCELLABLE"));
    }

    // ------------------------------------------------------------------ Requirement 3: two-person release

    @Test
    void initiatorCannotReleaseOwnFileButAnotherReviewerCan() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID initiator = UUID.randomUUID();
        UUID job = seedJob(tenant, initiator, "QUARANTINED");
        UUID q = seedQuarantine(tenant, job);
        String release = "{\"action\":\"RELEASE\",\"justification\":\"false positive confirmed\"}";

        String asInitiator = token(tenant, initiator, "ftp.quarantine.release", "ftp.audit.read");
        json(post("/api/v1/quarantine-controls/" + q + "/release"), asInitiator, release)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_RELEASE_BLOCKED"));
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT status FROM ftp.ftp_quarantine_record WHERE id = ?", String.class, q))
                .isEqualTo("QUARANTINED");

        // the denial itself was audited, and survives the rolled-back request
        json(get("/api/v1/audit?action=FTP_QUARANTINE_RELEASE_DENIED"), asInitiator, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].outcome").value("DENIED"));

        String reviewer = token(tenant, UUID.randomUUID(), "ftp.quarantine.release");
        json(post("/api/v1/quarantine-controls/" + q + "/release"), reviewer, release)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        json(post("/api/v1/quarantine-controls/" + q + "/release"), reviewer, release)
                .andExpect(status().isConflict());
    }

    @Test
    void releaseRequiresTheReleaseRoleNotJustTheWriteRole() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID q = seedQuarantine(tenant, seedJob(tenant, UUID.randomUUID(), "QUARANTINED"));
        String t = token(tenant, UUID.randomUUID(), "ftp.quarantine.write", "ftp.quarantine.read");
        json(post("/api/v1/quarantine-controls/" + q + "/release"), t, "{\"action\":\"RELEASE\",\"justification\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RBAC_DENIED"));
    }

    @Test
    void anotherTenantsQuarantineRecordIs403() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID q = seedQuarantine(tenantA, seedJob(tenantA, UUID.randomUUID(), "QUARANTINED"));
        String asB = token(UUID.randomUUID(), UUID.randomUUID(), "ftp.quarantine.release");
        json(post("/api/v1/quarantine-controls/" + q + "/release"), asB, "{\"action\":\"RELEASE\",\"justification\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
    }

    // ------------------------------------------------------------------ remaining endpoints

    @Test
    void checksumTrackingPoliciesAndConfigEndpointsAreWiredAndGuarded() throws Exception {
        UUID tenant = UUID.randomUUID();
        when(policyClient.listPolicies(tenant)).thenReturn(List.of(new PolicySummary("p1", "Transfer policy", "desc")));
        String t = token(tenant, UUID.randomUUID(), "ftp.checksum.read", "ftp.policy.read", "ftp.config.read", "ftp.config.write");

        json(get("/api/v1/checksum/audit-tracking"), t, null).andExpect(status().isOk());
        json(get("/api/v1/policies"), t, null).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("p1"));
        json(get("/api/v1/config"), t, null).andExpect(status().isOk());
        json(post("/api/v1/config"), t, "{\"key\":\"retry.max-attempts\",\"value\":\"5\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("5"));
        json(post("/api/v1/config"), t, "{\"key\":\"retry.max-attempts\",\"value\":\"99\"}")
                .andExpect(status().isUnprocessableEntity());
        json(post("/api/v1/config"), t, "{\"key\":\"sftp.password\",\"value\":\"hunter2\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONFIG_KEY_UNKNOWN"));

        String noRole = token(tenant, UUID.randomUUID(), "ftp.job.read");
        json(get("/api/v1/checksum/audit-tracking"), noRole, null).andExpect(status().isForbidden());
        json(get("/api/v1/policies"), noRole, null).andExpect(status().isForbidden());
        json(get("/api/v1/audit"), noRole, null).andExpect(status().isForbidden());
        json(get("/api/v1/quarantine-controls"), noRole, null).andExpect(status().isForbidden());
        json(get("/api/v1/endpoints"), noRole, null).andExpect(status().isForbidden());
    }
}
