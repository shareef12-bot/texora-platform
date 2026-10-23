package com.texora.secops.cgi.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kafka points at a dead address. Requests must still succeed and the audit must still be durable locally. */
class KafkaOutageIntegrationTest extends AbstractIntegrationTestBase {

    @DynamicPropertySource
    static void deadKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("cgi.audit.send-timeout-ms", () -> "600");
    }

    @BeforeEach
    void init() {
        identities();
    }

    @Test
    void kafkaOutageNeverBlocksOrFailsRequestHandling() throws Exception {
        String body = "{\"routePattern\":\"/legacy/outage\",\"approvedHandlerId\":\"stub-echo\",\"maxExecutionSeconds\":5}";
        String id = json(mvc.perform(post("/api/v1/route-allowlist").header("Authorization", ALL_ROLES_ALICE)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString());
        mvc.perform(post("/api/v1/route-allowlist/" + id + "/decision").header("Authorization", ALL_ROLES_BOB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}")).andExpect(status().isOk());

        long started = System.currentTimeMillis();
        mvc.perform(get("/gateway/legacy/outage").header("Authorization", ALL_ROLES_ALICE)).andExpect(status().isOk());
        assertThat(System.currentTimeMillis() - started).as("request must not wait for Kafka").isLessThan(3000);

        Integer durable = jdbc.queryForObject("select count(*) from cgi.cgi_audit_event where action = 'GATEWAY_EXECUTE' and tenant_id = ?::uuid",
                Integer.class, TENANT_A.toString());
        Integer unpublished = jdbc.queryForObject("select count(*) from cgi.cgi_audit_outbox where published_at is null", Integer.class);
        assertThat(durable).isGreaterThanOrEqualTo(1);
        assertThat(unpublished).isGreaterThanOrEqualTo(1);   // waiting in the outbox for the scheduled retry
    }

    private static String json(String s) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s).get("id").asText();
    }
}
