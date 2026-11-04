package com.texora.secops.vpn.it;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Endpoints incl. 401/403/404/409/422 paths, the two-person rule, tenant isolation, audit search and Kafka delivery. */
class VpnFlowIntegrationTest extends VpnIntegrationTestSupport {

    @Test
    void noTokenIs401AndTokenWithoutRoleIs403() {
        assertThat(get("/api/v1/access-policies", null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/v1/access-policies", "Bearer garbage").getStatusCode().value()).isEqualTo(401);
        var forbidden = get("/api/v1/access-policies", bearer(UUID.randomUUID(), UUID.randomUUID(), "vpn.audit.read"));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getBody().get("code")).isEqualTo("RBAC_DENIED");
        assertThat(forbidden.getBody()).containsKeys("timestamp", "status", "error", "code", "message");
    }

    @Test
    void everyAdminEndpointEnforcesItsRole() {
        String nobody = bearer(UUID.randomUUID(), UUID.randomUUID(), "some.other.role");
        for (String path : List.of("/api/v1/access-policies", "/api/v1/session-revocation", "/api/v1/gateway-integration",
                "/api/v1/config", "/api/v1/policies", "/api/v1/audit", "/api/v1/subjects")) {
            assertThat(get(path, nobody).getStatusCode().value()).as(path).isEqualTo(403);
        }
        assertThat(post("/api/v1/access-policies", nobody, map("name", "x", "conditions", map())).getStatusCode().value()).isEqualTo(403);
        assertThat(post("/api/v1/sessions", nobody, map()).getStatusCode().value()).isEqualTo(403);
        assertThat(post("/api/v1/session-revocation/" + UUID.randomUUID(), nobody, map("reason", "x")).getStatusCode().value()).isEqualTo(403);
        assertThat(post("/api/v1/config", nobody, map("key", "k", "value", "1")).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void dataplaneCallbackRequiresMtlsAndIgnoresBearerTokens() {
        String admin = bearer(UUID.randomUUID(), UUID.randomUUID(), ADMIN_ROLES);
        var r = post("/api/v1/gateway-integration/dataplane-events", admin,
                map("dataplaneSessionRef", "stub-x", "event", "TUNNEL_TERMINATED"));
        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getBody().get("code")).isEqualTo("MTLS_REQUIRED");
    }

    @Test
    void twoPersonApprovalIsEnforced() {
        UUID tenant = UUID.randomUUID();
        UUID both = UUID.randomUUID();
        String writerAndApprover = bearer(tenant, both, "vpn.policy.write", "vpn.policy.approve", "vpn.policy.read");
        var submitted = post("/api/v1/access-policies", writerAndApprover, map("name", "p", "conditions", map()));
        assertThat(submitted.getStatusCode().value()).isEqualTo(201);
        String id = (String) submitted.getBody().get("id");

        var self = post("/api/v1/access-policies/" + id + "/decision", writerAndApprover, map("decision", "APPROVE"));
        assertThat(self.getStatusCode().value()).isEqualTo(403);
        assertThat(self.getBody().get("code")).isEqualTo("SELF_APPROVAL_FORBIDDEN");
        assertThat(jdbc.queryForObject("select status from vpn.vpn_access_policy where id = ?::uuid", String.class, id))
                .isEqualTo("PENDING_APPROVAL");
        assertThat(auditActions(tenant)).contains("ACCESS_POLICY_SUBMITTED", "ACCESS_POLICY_SELF_APPROVAL_BLOCKED");

        String other = bearer(tenant, UUID.randomUUID(), "vpn.policy.approve");
        assertThat(post("/api/v1/access-policies/" + id + "/decision", other, map("decision", "APPROVE")).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/api/v1/access-policies/" + id + "/decision", other, map("decision", "APPROVE")).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void validationAndNotFoundPaths() {
        UUID tenant = UUID.randomUUID();
        String admin = bearer(tenant, UUID.randomUUID(), ADMIN_ROLES);
        assertThat(post("/api/v1/access-policies", admin, map("conditions", map())).getStatusCode().value()).isEqualTo(422); // no name
        assertThat(post("/api/v1/access-policies", admin, map("name", "x", "conditions", map("mfaRequried", false))).getStatusCode().value()).isEqualTo(422);
        assertThat(post("/api/v1/gateway-integration", admin, map("name", "x", "endpointUrl", "http://insecure")).getStatusCode().value()).isEqualTo(422);
        assertThat(post("/api/v1/config", admin, map("key", "session.max-minutes", "value", "99999")).getStatusCode().value()).isEqualTo(422);
        assertThat(post("/api/v1/config", admin, map("key", "not.a.key", "value", "1")).getStatusCode().value()).isEqualTo(422);
        assertThat(post("/api/v1/session-revocation/" + UUID.randomUUID(), admin, map("reason", "x")).getStatusCode().value()).isEqualTo(404);
        assertThat(rest.exchange("/api/v1/access-policies", HttpMethod.POST,
                new org.springframework.http.HttpEntity<>("{not json", jsonHeaders(admin)), Map.class).getStatusCode().value()).isEqualTo(400);
    }

    private org.springframework.http.HttpHeaders jsonHeaders(String bearer) {
        var h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.set("Authorization", bearer);
        return h;
    }

    @Test
    void configBoundsAndGuaranteeProtection() {
        String admin = bearer(UUID.randomUUID(), UUID.randomUUID(), ADMIN_ROLES);
        assertThat(post("/api/v1/config", admin, map("key", "session.max-minutes", "value", "60")).getStatusCode().value()).isEqualTo(200);
        // an interval that would let sessions go stale past the max-unverified horizon must be refused
        assertThat(post("/api/v1/config", admin, map("key", "session.revalidation-interval-seconds", "value", "600")).getStatusCode().value()).isEqualTo(422);
        assertThat(get("/api/v1/config", admin).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void fullLifecycleEstablishRevokeAuditAndKafka() {
        Fixture f = provision("ALLOW");

        // wrong MFA code is denied
        var badMfa = post("/api/v1/sessions", f.user, map("accessPolicyId", f.policyId.toString(), "deviceFingerprint", "fp-1", "mfaCode", "999999"));
        assertThat(badMfa.getStatusCode().value()).isEqualTo(403);
        assertThat(badMfa.getBody().get("code")).isEqualTo("SESSION_DENIED");

        var session = post("/api/v1/sessions", f.user, map("accessPolicyId", f.policyId.toString(), "deviceFingerprint", "fp-1", "mfaCode", "000000"));
        assertThat(session.getStatusCode().value()).isEqualTo(201);
        UUID sessionId = UUID.fromString((String) session.getBody().get("sessionId"));
        assertThat(stub.hasSession(sessionId)).isTrue();

        var list = get("/api/v1/session-revocation?status=ACTIVE", f.admin);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(list.getBody().toString()).contains(sessionId.toString());
        assertThat(get("/api/v1/policies", f.admin).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/gateway-integration", f.admin).getBody().toString()).contains("NOT a real VPN");

        // tenant isolation: another tenant's admin gets 403, not an empty result and not success
        String foreignAdmin = bearer(UUID.randomUUID(), UUID.randomUUID(), ADMIN_ROLES);
        var foreign = post("/api/v1/session-revocation/" + sessionId, foreignAdmin, map("reason", "x"));
        assertThat(foreign.getStatusCode().value()).isEqualTo(403);
        assertThat(sessionStatus(sessionId)).isEqualTo("ACTIVE");
        assertThat(get("/api/v1/audit", foreignAdmin).getBody().get("total")).isEqualTo(0);

        var revoked = post("/api/v1/session-revocation/" + sessionId, f.admin, map("reason", "lost laptop"));
        assertThat(revoked.getStatusCode().value()).isEqualTo(200);
        assertThat(revoked.getBody().get("status")).isEqualTo("REVOKED");
        assertThat(revoked.getBody().get("dataplaneRevocationConfirmed")).isEqualTo(true);
        assertThat(stub.hasSession(sessionId)).isFalse();

        assertThat(auditActions(f.tenant)).contains("ACCESS_POLICY_SUBMITTED", "ACCESS_POLICY_APPROVED", "VPN_SUBJECT_CREATED",
                "DEVICE_ENROLLED", "ACCESS_POLICY_ASSIGNED", "SESSION_ESTABLISHMENT_DENIED", "SESSION_ESTABLISHED", "SESSION_REVOKED_ADMIN");
        var audit = get("/api/v1/audit?action=SESSION_ESTABLISHED", f.admin);
        assertThat(audit.getStatusCode().value()).isEqualTo(200);
        assertThat((Integer) audit.getBody().get("total")).isEqualTo(1);

        // Kafka delivery via the outbox
        assertThat(publisher.publishDue()).isGreaterThan(0);
        assertThat(consumeActionsFor(f.tenant)).contains("SESSION_ESTABLISHED", "SESSION_REVOKED_ADMIN");
        assertThat(jdbc.queryForObject("select count(*) from vpn.vpn_audit_outbox where tenant_id = ? and published_at is null",
                Integer.class, f.tenant)).isZero();
    }

    private List<String> consumeActionsFor(UUID tenant) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p)) {
            consumer.subscribe(List.of("vpn.audit.events"));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline && !(actions.contains("SESSION_ESTABLISHED") && actions.contains("SESSION_REVOKED_ADMIN"))) {
                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(500))) {
                    if (tenant.toString().equals(rec.key())) {
                        String v = rec.value();
                        int i = v.indexOf("\"action\":\"");
                        actions.add(v.substring(i + 10, v.indexOf('"', i + 10)));
                    }
                }
            }
        }
        return actions;
    }
}
