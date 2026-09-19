package com.texora.secops.sec.stub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.sec.stub.SecStubApplication;
import com.texora.secops.sec.stub.dto.PolicyEvaluationRequest;
import com.texora.secops.sec.stub.dto.PolicyEvaluationResponse;
import com.texora.secops.sec.stub.dto.StubDecisionRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecStubApplication.class)
@AutoConfigureMockMvc
class PolicyEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TENANT  = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();

    @Test
    void evaluate_returns_PERMIT_for_VPN_CONNECT_via_configured_rule() throws Exception {
        PolicyEvaluationRequest req = new PolicyEvaluationRequest(
            TENANT, SUBJECT, "VPN_GATEWAY", UUID.randomUUID(), "CONNECT", null);

        MvcResult result = mockMvc.perform(post("/api/v1/policies/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

        PolicyEvaluationResponse resp = objectMapper.readValue(
            result.getResponse().getContentAsString(), PolicyEvaluationResponse.class);

        assertEquals("PERMIT", resp.getDecision());
        assertNotNull(resp.getEvaluatedAt());
        assertNotNull(resp.getRequestId());
    }

    @Test
    void evaluate_returns_DENY_for_AUDIT_LOG_DELETE_via_configured_rule() throws Exception {
        PolicyEvaluationRequest req = new PolicyEvaluationRequest(
            TENANT, SUBJECT, "AUDIT_LOG", null, "DELETE", null);

        MvcResult result = mockMvc.perform(post("/api/v1/policies/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

        PolicyEvaluationResponse resp = objectMapper.readValue(
            result.getResponse().getContentAsString(), PolicyEvaluationResponse.class);

        assertEquals("DENY", resp.getDecision());
    }

    @Test
    void evaluate_uses_default_DENY_when_no_rule_matches() throws Exception {
        PolicyEvaluationRequest req = new PolicyEvaluationRequest(
            TENANT, SUBJECT, "UNKNOWN_RESOURCE", null, "UNKNOWN_ACTION", null);

        MvcResult result = mockMvc.perform(post("/api/v1/policies/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

        PolicyEvaluationResponse resp = objectMapper.readValue(
            result.getResponse().getContentAsString(), PolicyEvaluationResponse.class);

        assertEquals("DENY", resp.getDecision()); // default-decision: DENY
    }

    @Test
    void evaluate_returns_400_when_tenantId_missing() throws Exception {
        // tenantId is null
        PolicyEvaluationRequest req = new PolicyEvaluationRequest(
            null, SUBJECT, "VPN_GATEWAY", null, "CONNECT", null);

        mockMvc.perform(post("/api/v1/policies/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void evaluate_returns_400_when_action_missing() throws Exception {
        PolicyEvaluationRequest req = new PolicyEvaluationRequest(
            TENANT, SUBJECT, "VPN_GATEWAY", null, null, null);

        mockMvc.perform(post("/api/v1/policies/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void runtime_rule_overrides_default_decision() throws Exception {
        // Add a DENY rule for a specific subject
        StubDecisionRule denyRule = new StubDecisionRule(
            null, null, SUBJECT, "DENY", "Test: subject-specific deny", null);

        mockMvc.perform(post("/api/v1/policies/stub/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(denyRule)))
            .andExpect(status().isOk());

        PolicyEvaluationRequest req = new PolicyEvaluationRequest(
            TENANT, SUBJECT, "ANY_RESOURCE", null, "ANY_ACTION", null);

        MvcResult result = mockMvc.perform(post("/api/v1/policies/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

        PolicyEvaluationResponse resp = objectMapper.readValue(
            result.getResponse().getContentAsString(), PolicyEvaluationResponse.class);

        assertEquals("DENY", resp.getDecision());

        // Cleanup: clear runtime rules so other tests are unaffected
        mockMvc.perform(delete("/api/v1/policies/stub/rules"))
            .andExpect(status().isNoContent());
    }

    @Test
    void list_rules_returns_configured_rules() throws Exception {
        mockMvc.perform(get("/api/v1/policies/stub/rules"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
