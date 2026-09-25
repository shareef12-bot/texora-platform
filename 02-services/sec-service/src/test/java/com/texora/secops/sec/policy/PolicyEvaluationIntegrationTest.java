package com.texora.secops.sec.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.sec.BaseIntegrationTest;
import com.texora.secops.sec.policy.dto.PolicyEvaluationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the policy evaluation endpoint.
 * Verifies default-deny behaviour end-to-end against real PostgreSQL.
 */
@AutoConfigureMockMvc
@DisplayName("POST /api/v1/policies/evaluate — Integration")
class PolicyEvaluationIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("Returns DENY/NO_MATCHING_POLICY when no policies exist for tenant")
    void noPolicy_returnsDenyDefaultDeny() throws Exception {
        PolicyEvaluationRequest request = buildRequest(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/policies/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.reason").value("NO_MATCHING_POLICY"))
                .andExpect(jsonPath("$.evaluatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Returns 400 when callerService is blank")
    void blankCallerService_returns400() throws Exception {
        PolicyEvaluationRequest request = buildRequest(UUID.randomUUID());
        request.setCallerService("");

        mockMvc.perform(post("/api/v1/policies/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns 400 when resource is blank")
    void blankResource_returns400() throws Exception {
        PolicyEvaluationRequest request = buildRequest(UUID.randomUUID());
        request.setResource("");

        mockMvc.perform(post("/api/v1/policies/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Response always has decision field — never null")
    void response_alwaysHasDecisionField() throws Exception {
        PolicyEvaluationRequest request = buildRequest(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/policies/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").exists())
                .andExpect(jsonPath("$.reason").exists());
    }

    private PolicyEvaluationRequest buildRequest(UUID tenantId) {
        PolicyEvaluationRequest req = new PolicyEvaluationRequest();
        req.setCallerService("sso");
        req.setSubject(new PolicyEvaluationRequest.SubjectDto("USER", UUID.randomUUID().toString()));
        req.setResource("sso:session");
        req.setAction("create");
        return req;
    }
}
