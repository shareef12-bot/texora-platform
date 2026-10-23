package com.texora.secops.vpn.it;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import com.texora.secops.vpn.audit.AuditOutboxPublisher;
import com.texora.secops.vpn.gateway.LocalStubGatewayIntegrationAdapter;
import com.texora.secops.vpn.security.PolicyDecisionClient;
import com.texora.secops.vpn.security.SecDecision;
import com.texora.secops.vpn.session.SessionControlService;
import com.texora.secops.vpn.session.SessionRevalidationScheduler;
import com.texora.secops.vpn.subject.DcIdentityClient;
import com.texora.secops.vpn.subject.DcIdentityResult;
import org.junit.jupiter.api.BeforeEach;
import org.objenesis.ObjenesisStd;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared Testcontainers (PostgreSQL + Kafka) and HTTP helpers. These tests need Docker; without it they fail with
 * "Could not find a valid Docker environment" - that is NOT an application bug (KNOWN-INTEGRATION-PITFALLS #9).
 * Each test uses a fresh random tenant, so nothing needs cleaning up (vpn_audit_event cannot be truncated, by design).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "vpn.scheduling.enabled=false",
        "vpn.gateway.adapter=stub",
        "vpn.mfa.stub-enabled=true",
        "spring.kafka.producer.properties.max.block.ms=3000"
})
abstract class VpnIntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean
    TokenValidator tokenValidator;
    @MockitoBean
    PolicyDecisionClient sec;
    @MockitoBean
    DcIdentityClient dc;

    @Autowired
    TestRestTemplate rest;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SessionRevalidationScheduler scheduler;
    @Autowired
    SessionControlService control;
    @Autowired
    AuditOutboxPublisher publisher;
    @Autowired
    LocalStubGatewayIntegrationAdapter stub;

    private final Map<String, TokenClaims> tokens = new HashMap<>();

    static final String[] ADMIN_ROLES = {"vpn.policy.read", "vpn.policy.write", "vpn.policy.assign", "vpn.subject.read",
            "vpn.subject.write", "vpn.device.read", "vpn.device.write", "vpn.gateway.read", "vpn.gateway.write",
            "vpn.session.read", "vpn.session.revoke", "vpn.audit.read", "vpn.config.read", "vpn.config.write"};

    @BeforeEach
    void resetMocksAndAuth() {
        tokens.clear();
        stub.setSimulatedOutage(false);
        when(tokenValidator.validate(any())).thenAnswer(inv -> {
            String header = inv.getArgument(0);
            TokenClaims c = header == null ? null : tokens.get(header);
            if (c == null) {
                throw new ObjenesisStd().newInstance(PlatformException.UnauthenticatedException.class);
            }
            return c;
        });
        when(sec.decide(any(), anyInt())).thenReturn(SecDecision.ALLOW);
        when(dc.lookup(any(), any())).thenReturn(DcIdentityResult.ACTIVE);
    }

    // ---------------------------------------------------------------- auth helpers

    /** Registers a mock identity and returns the "Authorization" header value for it. */
    String bearer(UUID tenant, UUID user, String... roles) {
        TokenClaims claims = mock(TokenClaims.class);
        when(claims.getTenantId()).thenReturn(tenant);
        when(claims.getSubject()).thenReturn(user);
        for (String r : roles) {
            when(claims.hasRole(r)).thenReturn(true);
        }
        String header = "Bearer " + UUID.randomUUID();
        tokens.put(header, claims);
        return header;
    }

    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> call(HttpMethod method, String path, String bearer, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.set("Authorization", bearer);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> post(String path, String bearer, Object body) {
        return call(HttpMethod.POST, path, bearer, body);
    }

    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> get(String path, String bearer) {
        return call(HttpMethod.GET, path, bearer, null);
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ---------------------------------------------------------------- scenario fixture

    static final class Fixture {
        UUID tenant;
        UUID vpnUserId;
        String admin;
        String approver;
        String user;
        UUID policyId;
        UUID subjectId;
        UUID sessionId;
    }

    /** Policy submitted + approved by two different people, subject, device, assignment, gateway endpoint - no session yet. */
    Fixture provision(String conditionsEffect) {
        Fixture f = new Fixture();
        f.tenant = UUID.randomUUID();
        f.vpnUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        f.admin = bearer(f.tenant, adminId, ADMIN_ROLES);
        f.approver = bearer(f.tenant, approverId, "vpn.policy.approve", "vpn.policy.read");
        f.user = bearer(f.tenant, f.vpnUserId, "vpn.session.establish");

        var submitted = post("/api/v1/access-policies", f.admin, map("name", "eng-access", "conditions", map("effect", conditionsEffect)));
        assertStatus(submitted, 201);
        f.policyId = UUID.fromString((String) submitted.getBody().get("id"));
        assertStatus(post("/api/v1/access-policies/" + f.policyId + "/decision", f.approver, map("decision", "APPROVE")), 200);

        var subject = post("/api/v1/subjects", f.admin, map("dcUserId", f.vpnUserId.toString()));
        assertStatus(subject, 201);
        f.subjectId = UUID.fromString((String) subject.getBody().get("id"));
        assertStatus(post("/api/v1/subjects/" + f.subjectId + "/devices", f.admin,
                map("deviceFingerprint", "fp-1", "mfaFactorRef", "factor-1")), 201);
        assertStatus(post("/api/v1/access-policies/" + f.policyId + "/assignments", f.admin, map("subjectId", f.subjectId.toString())), 201);
        assertStatus(post("/api/v1/gateway-integration", f.admin, map("name", "gw-1", "endpointUrl", "https://gw.example.internal")), 201);
        return f;
    }

    Fixture provisionWithActiveSession() {
        Fixture f = provision("ALLOW");
        var session = post("/api/v1/sessions", f.user, map("accessPolicyId", f.policyId.toString(),
                "deviceFingerprint", "fp-1", "mfaCode", "000000"));
        assertStatus(session, 201);
        f.sessionId = UUID.fromString((String) session.getBody().get("sessionId"));
        return f;
    }

    @SuppressWarnings("rawtypes")
    static void assertStatus(ResponseEntity<Map> response, int expected) {
        if (response.getStatusCode().value() != expected) {
            throw new AssertionError("expected HTTP " + expected + " but got " + response.getStatusCode().value()
                    + " body=" + response.getBody());
        }
    }

    // ---------------------------------------------------------------- DB probes

    String sessionStatus(UUID sessionId) {
        return jdbc.queryForObject("select status from vpn.vpn_session where id = ?", String.class, sessionId);
    }

    String sessionTrigger(UUID sessionId) {
        return jdbc.queryForObject("select revocation_trigger from vpn.vpn_session where id = ?", String.class, sessionId);
    }

    String teardownState(UUID sessionId) {
        return jdbc.queryForObject("select teardown_state from vpn.vpn_gateway_binding where session_id = ?", String.class, sessionId);
    }

    void makeDue(UUID sessionId) {
        jdbc.update("update vpn.vpn_session set next_validation_at = now() - interval '1 second' where id = ?", sessionId);
    }

    List<String> auditActions(UUID tenant) {
        return jdbc.queryForList("select action from vpn.vpn_audit_event where tenant_id = ? order by occurred_at", String.class, tenant);
    }
}
