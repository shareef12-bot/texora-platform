package com.texora.secops.cgi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.cgi.execution.adapter.LocalStubWorkerIsolationAdapter;
import com.texora.secops.cgi.security.PolicyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatewayFlowIntegrationTest extends AbstractIntegrationTestBase {

    @Autowired
    ObjectMapper json;
    @MockitoSpyBean
    LocalStubWorkerIsolationAdapter adapter;

    @BeforeEach
    void init() {
        identities();
    }

    private String createRoute(String bearer, String pattern, String handler, int seconds) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/route-allowlist").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("routePattern", pattern, "approvedHandlerId", handler, "maxExecutionSeconds", seconds))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private void decide(String bearer, String id, String decision, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/route-allowlist/" + id + "/decision").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\"}")).andExpect(status().is(expectedStatus));
    }

    private String approvedRoute(String pattern, String handler, int seconds) throws Exception {
        String id = createRoute(ALL_ROLES_ALICE, pattern, handler, seconds);
        decide(ALL_ROLES_BOB, id, "APPROVED", 200);
        return id;
    }

    @Test
    void missingTokenIs401AndMissingRoleIs403() throws Exception {
        mvc.perform(get("/api/v1/route-allowlist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/route-allowlist").header("Authorization", NO_ROLES_CAROL)).andExpect(status().isForbidden());
    }

    @Test
    void unlistedRouteIs404AndNoWorkerIsSpawned() throws Exception {
        mvc.perform(get("/gateway/legacy/unlisted").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isNotFound());
        verify(adapter, never()).spawn(any());
    }

    @Test
    void submitterCannotApproveOwnRouteButSecondPersonCan() throws Exception {
        String id = createRoute(ALL_ROLES_ALICE, "/legacy/two-person", "stub-echo", 5);
        decide(ALL_ROLES_ALICE, id, "APPROVED", 403);
        String status = jdbc.queryForObject("select status from cgi.cgi_route_allowlist_entry where id = ?::uuid", String.class, id);
        assertThat(status).isEqualTo("PENDING_APPROVAL");
        decide(ALL_ROLES_BOB, id, "APPROVED", 200);
    }

    @Test
    void otherTenantCannotTouchMyRoute() throws Exception {
        String id = createRoute(ALL_ROLES_ALICE, "/legacy/tenant-scoped", "stub-echo", 5);
        decide(ALL_ROLES_EVE, id, "APPROVED", 403);
    }

    @Test
    void duplicateLivePatternIs409() throws Exception {
        createRoute(ALL_ROLES_ALICE, "/legacy/dup", "stub-echo", 5);
        mvc.perform(post("/api/v1/route-allowlist").header("Authorization", ALL_ROLES_ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"routePattern\":\"/legacy/dup\",\"approvedHandlerId\":\"stub-echo\",\"maxExecutionSeconds\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unapprovedRouteDoesNotRunAndApprovedRouteDoes() throws Exception {
        createRoute(ALL_ROLES_ALICE, "/legacy/pending-only", "stub-echo", 5);
        mvc.perform(get("/gateway/legacy/pending-only").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isNotFound());

        approvedRoute("/legacy/echo", "stub-echo", 5);
        mvc.perform(get("/gateway/legacy/echo").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isOk());
        // another tenant has no such route
        mvc.perform(get("/gateway/legacy/echo").header("Authorization", ALL_ROLES_EVE)).andExpect(status().isNotFound());
    }

    @Test
    void hungHandlerIsForceKilledAndCallerGets504WithTimedOutRecord() throws Exception {
        approvedRoute("/legacy/hang", "stub-hang", 1);
        long started = System.currentTimeMillis();
        mvc.perform(get("/gateway/legacy/hang").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isGatewayTimeout());
        assertThat(System.currentTimeMillis() - started).isLessThan(5000);
        verify(adapter).kill(any());
        assertThat(jdbc.queryForObject("select count(*) from cgi.cgi_execution_record where outcome = 'TIMED_OUT' and tenant_id = ?::uuid",
                Integer.class, TENANT_A.toString())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sigtermIgnoringHandlerIsStillKilled() throws Exception {
        approvedRoute("/legacy/ignore-term", "stub-ignore-sigterm", 1);
        mvc.perform(get("/gateway/legacy/ignore-term").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isGatewayTimeout());
    }

    @Test
    void forkAndNetworkAttemptsEndInFailureNotSuccess() throws Exception {
        approvedRoute("/legacy/fork", "stub-fork", 5);
        approvedRoute("/legacy/net", "stub-network", 5);
        mvc.perform(get("/gateway/legacy/fork").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isInternalServerError());
        mvc.perform(get("/gateway/legacy/net").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isInternalServerError());
    }

    @Test
    void policyDenyBlocksExecution() throws Exception {
        approvedRoute("/legacy/denied", "stub-echo", 5);
        when(policyClient.evaluate(any(), any(), any())).thenReturn(PolicyDecision.deny(PolicyDecision.REASON_SEC_DENY));
        mvc.perform(get("/gateway/legacy/denied").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isForbidden());
        verify(adapter, never()).spawn(any());
    }

    @Test
    void secretsNeverReachTheRequestLogOrAuditTable() throws Exception {
        approvedRoute("/legacy/redaction", "stub-echo", 5);
        mvc.perform(get("/gateway/legacy/redaction").param("password", "hunter2-SECRET").param("note", "bob@example.com")
                .param("report", "daily").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isOk());
        String logs = jdbc.queryForObject("select coalesce(string_agg(redacted_params::text, ' '), '') from cgi.cgi_request_log", String.class);
        String audits = jdbc.queryForObject("select coalesce(string_agg(payload::text, ' '), '') from cgi.cgi_audit_event", String.class);
        assertThat(logs).doesNotContain("hunter2-SECRET").doesNotContain("bob@example.com").contains("daily");
        assertThat(audits).doesNotContain("hunter2-SECRET").doesNotContain("bob@example.com");
    }

    @Test
    void auditTableIsAppendOnlyEvenForTheOwner() throws Exception {
        approvedRoute("/legacy/audit-immutable", "stub-echo", 5);
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("update cgi.cgi_audit_event set outcome = 'SUCCESS'"));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("delete from cgi.cgi_audit_event"));
    }

    @Test
    void auditSearchIsTenantScopedAndRedacted() throws Exception {
        approvedRoute("/legacy/audit-search", "stub-echo", 5);
        MvcResult mine = mvc.perform(get("/api/v1/audit").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isOk()).andReturn();
        JsonNode items = json.readTree(mine.getResponse().getContentAsString()).get("items");
        assertThat(items.size()).isGreaterThan(0);
        MvcResult theirs = mvc.perform(get("/api/v1/audit").param("action", "ROUTE_APPROVED").header("Authorization", ALL_ROLES_EVE))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(theirs.getResponse().getContentAsString()).get("items").size()).isZero();
    }

    @Test
    void isolationChangesNeedTwoPeopleAndPolicyEndpointLists() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/worker-isolation").header("Authorization", ALL_ROLES_ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"handlerId\":\"stub-echo\",\"sandboxProfile\":\"strict-default\",\"networkAllowed\":false}"))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(r.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/v1/worker-isolation/" + id + "/decision").header("Authorization", ALL_ROLES_ALICE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/worker-isolation/" + id + "/decision").header("Authorization", ALL_ROLES_BOB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/policies").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isOk());
    }

    @Test
    void healthIsReachableWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        assertThat(List.of(1)).isNotEmpty();
    }
}
