package com.texora.secops.vpn.it;

import com.texora.secops.vpn.security.SecDecision;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Fault injection against real PostgreSQL: SEC unavailable during BOTH establishment and active-session re-validation,
 * dataplane outage during establishment and revocation, stale-validation watchdog, policy disablement.
 * No session may silently continue on an ambiguous state, and no teardown may be forgotten.
 */
class FailClosedIntegrationTest extends VpnIntegrationTestSupport {

    private int activeSessions(UUID tenant) {
        return jdbc.queryForObject("select count(*) from vpn.vpn_session where tenant_id = ? and status = 'ACTIVE'", Integer.class, tenant);
    }

    @Test
    void secUnavailableDuringEstablishmentDeniesAndCreatesNothing() {
        Fixture f = provision("ALLOW");
        when(sec.decide(any(), anyInt())).thenReturn(SecDecision.UNAVAILABLE);
        var r = post("/api/v1/sessions", f.user, map("accessPolicyId", f.policyId.toString(), "deviceFingerprint", "fp-1", "mfaCode", "000000"));
        assertThat(r.getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from vpn.vpn_session where tenant_id = ?", Integer.class, f.tenant)).isZero();
        assertThat(auditActions(f.tenant)).contains("SESSION_ESTABLISHMENT_DENIED");
    }

    @Test
    void secUnavailableDuringRevalidationRevokesAndTearsDownTheTunnel() {
        Fixture f = provisionWithActiveSession();
        assertThat(stub.hasSession(f.sessionId)).isTrue();

        // SEC healthy: re-validation confirms and the session stays ACTIVE
        makeDue(f.sessionId);
        scheduler.revalidateDue();
        assertThat(sessionStatus(f.sessionId)).isEqualTo("ACTIVE");
        assertThat(stub.hasSession(f.sessionId)).isTrue();

        // SEC goes away: uncertainty -> proactive revocation
        when(sec.decide(any(), anyInt())).thenReturn(SecDecision.UNAVAILABLE);
        makeDue(f.sessionId);
        scheduler.revalidateDue();
        assertThat(sessionStatus(f.sessionId)).isEqualTo("REVOKED");
        assertThat(sessionTrigger(f.sessionId)).isEqualTo("SEC_UNAVAILABLE");
        assertThat(stub.hasSession(f.sessionId)).as("tunnel torn down, not left up").isFalse();
        assertThat(teardownState(f.sessionId)).isEqualTo("CONFIRMED");
        assertThat(auditActions(f.tenant)).contains("SESSION_REVOKED_UNCERTAINTY");
    }

    @Test
    void secUnavailableAndDataplaneDownStillRevokesAndRetriesUntilConfirmed() {
        Fixture f = provisionWithActiveSession();
        when(sec.decide(any(), anyInt())).thenReturn(SecDecision.UNAVAILABLE);
        stub.setSimulatedOutage(true);
        control.revalidate(f.tenant, f.sessionId);

        assertThat(sessionStatus(f.sessionId)).isEqualTo("REVOKED");
        assertThat(teardownState(f.sessionId)).isEqualTo("PENDING");

        stub.setSimulatedOutage(false);
        jdbc.update("update vpn.vpn_gateway_binding set last_teardown_attempt_at = now() - interval '1 hour' where session_id = ?", f.sessionId);
        assertThat(control.retryPendingTeardowns()).isGreaterThanOrEqualTo(1);
        assertThat(teardownState(f.sessionId)).isEqualTo("CONFIRMED");
        assertThat(stub.hasSession(f.sessionId)).isFalse();
    }

    @Test
    void dataplaneOutageDuringEstablishmentIs504AndNothingStaysActive() {
        Fixture f = provision("ALLOW");
        stub.setSimulatedOutage(true);
        var r = post("/api/v1/sessions", f.user, map("accessPolicyId", f.policyId.toString(), "deviceFingerprint", "fp-1", "mfaCode", "000000"));
        assertThat(r.getStatusCode().value()).isEqualTo(504);
        assertThat(activeSessions(f.tenant)).isZero();
        assertThat(jdbc.queryForObject("select teardown_state from vpn.vpn_gateway_binding where tenant_id = ?", String.class, f.tenant))
                .isEqualTo("PENDING");
    }

    @Test
    void adminRevokeDuringDataplaneOutageReportsUnconfirmedThenCompletes() {
        Fixture f = provisionWithActiveSession();
        stub.setSimulatedOutage(true);
        var r = post("/api/v1/session-revocation/" + f.sessionId, f.admin, map("reason", "incident"));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody().get("status")).isEqualTo("REVOKED");
        assertThat(r.getBody().get("dataplaneRevocationConfirmed")).isEqualTo(false);
        stub.setSimulatedOutage(false);
        var again = post("/api/v1/session-revocation/" + f.sessionId, f.admin, map("reason", "incident"));
        assertThat(again.getBody().get("dataplaneRevocationConfirmed")).isEqualTo(true);
    }

    @Test
    void watchdogRevokesASessionThatRevalidationNeverReached() {
        Fixture f = provisionWithActiveSession();
        jdbc.update("update vpn.vpn_session set last_validated_at = now() - interval '1 hour' where id = ?", f.sessionId);
        scheduler.watchdog();
        assertThat(sessionStatus(f.sessionId)).isEqualTo("REVOKED");
        assertThat(sessionTrigger(f.sessionId)).isEqualTo("STALE_VALIDATION");
        assertThat(stub.hasSession(f.sessionId)).isFalse();
    }

    @Test
    void watchdogExpiresSessionsPastTheirLifetime() {
        Fixture f = provisionWithActiveSession();
        jdbc.update("update vpn.vpn_session set expires_at = now() - interval '1 second' where id = ?", f.sessionId);
        scheduler.watchdog();
        assertThat(sessionStatus(f.sessionId)).isEqualTo("EXPIRED");
        assertThat(stub.hasSession(f.sessionId)).isFalse();
    }

    @Test
    void disablingAPolicyForcesImmediateRevalidationAndRevokes() {
        Fixture f = provisionWithActiveSession();
        var disabled = post("/api/v1/access-policies/" + f.policyId + "/disable", f.approver, null);
        assertThat(disabled.getStatusCode().value()).isEqualTo(200);
        // the disable marked the session due; no waiting for the periodic interval
        scheduler.revalidateDue();
        assertThat(sessionStatus(f.sessionId)).isEqualTo("REVOKED");
        assertThat(sessionTrigger(f.sessionId)).isEqualTo("POLICY_CHANGED");
        assertThat(stub.hasSession(f.sessionId)).isFalse();
    }

    @Test
    void replacingAPolicyWithoutTheSubmitterApprovingItRetiresTheOldOne() {
        Fixture f = provisionWithActiveSession();
        var next = post("/api/v1/access-policies", f.admin, map("name", "v2", "replacesPolicyId", f.policyId.toString(),
                "conditions", map("effect", "ALLOW")));
        assertThat(next.getStatusCode().value()).isEqualTo(201);
        String nextId = (String) next.getBody().get("id");
        assertThat(post("/api/v1/access-policies/" + nextId + "/decision", f.approver, map("decision", "APPROVE")).getStatusCode().value()).isEqualTo(200);
        scheduler.revalidateDue();
        assertThat(sessionStatus(f.sessionId)).isEqualTo("REVOKED");
        assertThat(sessionTrigger(f.sessionId)).isEqualTo("POLICY_CHANGED");
    }
}
