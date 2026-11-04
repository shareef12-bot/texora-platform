package com.texora.qap.execution;

import com.texora.qap.execution.adapter.PayloadRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadRedactorTest {

    private PayloadRedactor redactor;

    @BeforeEach
    void setUp() {
        redactor = new PayloadRedactor(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void redactsPassword() {
        String json = "{\"username\":\"alice\",\"password\":\"s3cr3t\"}";
        String result = redactor.redact(json);
        assertThat(result).contains("\"username\":\"alice\"");
        assertThat(result).contains("\"password\":\"[REDACTED]\"");
        assertThat(result).doesNotContain("s3cr3t");
    }

    @Test
    void redactsAccessToken() {
        String json = "{\"access_token\":\"eyJhbGc...\",\"expires_in\":3600}";
        String result = redactor.redact(json);
        assertThat(result).contains("\"[REDACTED]\"");
        assertThat(result).doesNotContain("eyJhbGc");
    }

    @Test
    void redactsMultipleSensitiveFields() {
        String json = "{\"token\":\"t1\",\"apiKey\":\"k1\",\"data\":\"ok\"}";
        String result = redactor.redact(json);
        assertThat(result).contains("\"data\":\"ok\"");
        assertThat(result).doesNotContain("t1");
        assertThat(result).doesNotContain("k1");
    }

    @Test
    void handlesNullPayload() {
        String result = redactor.redact(null);
        assertThat(result).isNull();
    }

    @Test
    void handlesEmptyPayload() {
        String result = redactor.redact("");
        assertThat(result).isEqualTo("");
    }

    @Test
    void handlesInvalidJson() {
        String result = redactor.redact("not-json");
        assertThat(result).isEqualTo("[REDACTED-PARSE-ERROR]");
    }

    @Test
    void preservesNonSensitiveFields() {
        String json = "{\"name\":\"test\",\"status\":\"PASS\",\"durationMs\":123}";
        String result = redactor.redact(json);
        assertThat(result).contains("\"name\":\"test\"");
        assertThat(result).contains("\"status\":\"PASS\"");
        assertThat(result).contains("\"durationMs\":123");
    }

    @Test
    void redactsCreditCard() {
        String json = "{\"cardNumber\":\"4111111111111111\",\"amount\":100}";
        String result = redactor.redact(json);
        assertThat(result).doesNotContain("4111111111111111");
        assertThat(result).contains("\"amount\":100");
    }
}
