package com.texora.secops.dc;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests (real PostgreSQL + Kafka via Testcontainers; needs Docker - pitfall #9).
 * The shared TokenValidator is replaced by a mock because real SSO tokens are out of scope here.
 * UNVERIFIED in the authoring environment: no Docker/Maven was available - run in CI.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DcApiIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    TokenValidator tokenValidator;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

    private String token(String name, UUID tenant, UUID subject, String... roles) {
        String header = "Bearer " + name + "-" + UUID.randomUUID();
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(tenant);
        when(c.getSubject()).thenReturn(subject);
        when(c.getRoles()).thenReturn(new HashSet<String>(Arrays.asList(roles)));
        for (String role : roles) {
            when(c.hasRole(role)).thenReturn(true);
        }
        when(tokenValidator.validate(header)).thenReturn(c);
        return header;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> call(HttpMethod method, String path, String authorization, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            headers.set("Authorization", authorization);
        }
        return rest.exchange(path, method, new HttpEntity<String>(json, headers), Map.class);
    }

    @Test
    void unauthenticatedRequestIs401() {
        ResponseEntity<Map> r = call(HttpMethod.GET, "/api/v1/groups", null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        assertEquals("UNAUTHENTICATED", r.getBody().get("code"));
    }

    @Test
    void missingRoleIs403() {
        String t = token("noRole", tenantA, UUID.randomUUID(), "dc.user.read");
        ResponseEntity<Map> r = call(HttpMethod.GET, "/api/v1/groups", t, null);
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("RBAC_DENIED", r.getBody().get("code"));
    }

    @Test
    void groupCreateThenDuplicateIs409() {
        String t = token("admin", tenantA, UUID.randomUUID(), "dc.group.write", "dc.group.read");
        String body = "{\"name\":\"vpn-admins\",\"description\":\"VPN administrators\"}";
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/api/v1/groups", t, body).getStatusCode());
        ResponseEntity<Map> dup = call(HttpMethod.POST, "/api/v1/groups", t, body);
        assertEquals(HttpStatus.CONFLICT, dup.getStatusCode());
        assertEquals("GROUP_EXISTS", dup.getBody().get("code"));
        assertEquals(HttpStatus.OK, call(HttpMethod.GET, "/api/v1/groups", t, null).getStatusCode());
    }

    @Test
    void validationFailureIs422() {
        String t = token("admin", tenantA, UUID.randomUUID(), "dc.policy.write");
        ResponseEntity<Map> r = call(HttpMethod.POST, "/api/v1/policies", t, "{\"subjectType\":\"BOGUS\",\"resource\":\"x\",\"action\":\"y\",\"effect\":\"ALLOW\"}");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, r.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void twoPersonConfigApprovalIsEnforcedEndToEnd() {
        UUID submitter = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        String ts = token("submitter", tenantA, submitter, "dc.config.write", "dc.config.approve");
        String ta = token("approver", tenantA, approver, "dc.config.approve");

        ResponseEntity<Map> created = call(HttpMethod.POST, "/api/v1/config", ts, "{\"key\":\"session.timeout\",\"value\":\"30\"}");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("PENDING_APPROVAL", created.getBody().get("status"));
        String id = String.valueOf(created.getBody().get("id"));

        ResponseEntity<Map> self = call(HttpMethod.POST, "/api/v1/config/" + id + "/decision", ts, "{\"decision\":\"APPROVED\"}");
        assertEquals(HttpStatus.FORBIDDEN, self.getStatusCode());
        assertEquals("SELF_APPROVAL_FORBIDDEN", self.getBody().get("code"));

        ResponseEntity<Map> ok = call(HttpMethod.POST, "/api/v1/config/" + id + "/decision", ta, "{\"decision\":\"APPROVED\"}");
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals("APPROVED", ok.getBody().get("status"));

        assertEquals(HttpStatus.CONFLICT, call(HttpMethod.POST, "/api/v1/config/" + id + "/decision", ta, "{\"decision\":\"APPROVED\"}").getStatusCode());

        // the denied self-approval attempt was audited even though that request was rejected
        Integer denied = jdbc.queryForObject("select count(*) from dc.dc_audit_event where tenant_id = ? and action = 'CONFIG_DECISION_DENIED'",
                Integer.class, tenantA);
        assertTrue(denied != null && denied >= 1);
    }

    @Test
    void crossTenantAccessIs403NotEmptyOr404() {
        String owner = token("ownerA", tenantA, UUID.randomUUID(), "dc.config.write");
        String id = String.valueOf(call(HttpMethod.POST, "/api/v1/config", owner, "{\"key\":\"x.y\",\"value\":\"1\"}").getBody().get("id"));
        String otherTenant = token("approverB", tenantB, UUID.randomUUID(), "dc.config.approve");
        ResponseEntity<Map> r = call(HttpMethod.POST, "/api/v1/config/" + id + "/decision", otherTenant, "{\"decision\":\"APPROVED\"}");
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("TENANT_MISMATCH", r.getBody().get("code"));
        // a truly unknown id is a plain 404
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.POST, "/api/v1/config/" + UUID.randomUUID() + "/decision", otherTenant, "{\"decision\":\"APPROVED\"}").getStatusCode());
    }

    @Test
    void hrmsOwnedFieldsAreRejectedAndEmployeeCanBeProjected() {
        String t = token("admin", tenantA, UUID.randomUUID(), "dc.user.write");
        ResponseEntity<Map> bad = call(HttpMethod.POST, "/api/v1/users", t, "{\"hrmsEmployeeId\":\"EMP-1001\",\"username\":\"hacker\"}");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, bad.getStatusCode());
        assertEquals("HRMS_OWNED_FIELD", bad.getBody().get("code"));
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/api/v1/users", t, "{\"hrmsEmployeeId\":\"EMP-1001\"}").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, call(HttpMethod.POST, "/api/v1/users", t, "{\"hrmsEmployeeId\":\"EMP-1001\"}").getStatusCode());
    }

    @Test
    void policyAssignmentToSelfIsBlocked() {
        UUID adminId = UUID.randomUUID();
        jdbc.update("insert into dc.dc_user (id, tenant_id, username, status, display_name, hrms_employee_id, security_locked, created_at, updated_at) "
                + "values (?, ?, 'selfadmin', 'ACTIVE', 'Self Admin', 'EMP-SELF', false, now(), now())", adminId, tenantA);
        String t = token("policyAdmin", tenantA, adminId, "dc.policy.write", "dc.policy.assign");
        ResponseEntity<Map> p = call(HttpMethod.POST, "/api/v1/policies", t,
                "{\"subjectType\":\"USER\",\"resource\":\"vpn\",\"action\":\"admin\",\"effect\":\"ALLOW\"}");
        assertEquals(HttpStatus.CREATED, p.getStatusCode());
        String body = "{\"policyId\":\"" + p.getBody().get("id") + "\",\"subjectType\":\"USER\",\"subjectId\":\"" + adminId + "\"}";
        ResponseEntity<Map> r = call(HttpMethod.POST, "/api/v1/policy-assignment", t, body);
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("PRIVILEGE_ESCALATION_BLOCKED", r.getBody().get("code"));
    }

    @Test
    void auditSearchIsTenantScoped() {
        String a = token("auditA", tenantA, UUID.randomUUID(), "dc.group.write", "dc.audit.read");
        call(HttpMethod.POST, "/api/v1/groups", a, "{\"name\":\"audit-probe\"}");
        String b = token("auditB", tenantB, UUID.randomUUID(), "dc.audit.read");
        ResponseEntity<Map> rb = call(HttpMethod.GET, "/api/v1/audit?action=GROUP_CREATED", b, null);
        assertEquals(HttpStatus.OK, rb.getStatusCode());
        assertEquals(0, ((Number) rb.getBody().get("totalElements")).intValue());
        ResponseEntity<Map> ra = call(HttpMethod.GET, "/api/v1/audit?action=GROUP_CREATED", a, null);
        assertTrue(((Number) ra.getBody().get("totalElements")).intValue() >= 1);
    }

    @Test
    void auditTableIsImmutableAtTheDatabase() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into dc.dc_audit_event (id, tenant_id, actor_id, action, target_type, outcome, occurred_at) values (?, ?, ?, 'X', 'T', 'SUCCESS', ?)",
                id, tenantA, UUID.randomUUID(), java.sql.Timestamp.from(Instant.now()));
        assertThrows(DataAccessException.class, () -> jdbc.update("update dc.dc_audit_event set outcome = 'TAMPERED' where id = ?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from dc.dc_audit_event where id = ?", id));
    }

    @Test
    void everyTableCarriesTenantId() {
        List<String> missing = jdbc.queryForList(
                "select table_name from information_schema.tables t where table_schema = 'dc' and table_type = 'BASE TABLE' "
                        + "and table_name not like 'flyway%' and not exists (select 1 from information_schema.columns c "
                        + "where c.table_schema = 'dc' and c.table_name = t.table_name and c.column_name = 'tenant_id')", String.class);
        assertTrue(missing.isEmpty(), "tables without tenant_id: " + missing);
    }
}
