package com.texora.secops.cgi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.cgi.audit.service.ParameterRedactionService;
import com.texora.secops.cgi.config.props.RedactionProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redaction regression corpus. Coverage gaps are a SILENT leak: when a new legacy handler is onboarded, add a row
 * here for every sensitive parameter that handler accepts (name + realistic value). The build then proves the secret
 * never survives into anything that would be persisted or published.
 */
class RedactionRegressionTest {

    private final ParameterRedactionService service = new ParameterRedactionService(new RedactionProperties(), new ObjectMapper());

    static Stream<Arguments> corpus() {
        return Stream.of(
                Arguments.of("password", "S3cr3t-Passw0rd!"),
                Arguments.of("passwd", "hunter2hunter2"),
                Arguments.of("api_key", "FAKE_STRIPE_SECRET_FOR_REDACTION_TEST"),
                Arguments.of("apiKey", "plainlooking"),
                Arguments.of("access_token", "opaque-token-value"),
                Arguments.of("Authorization", "Bearer abcdef123456"),
                Arguments.of("cookie", "SESSIONID=abc123"),
                Arguments.of("sessionId", "abc123xyz"),
                Arguments.of("client_secret", "shhh-its-a-secret"),
                Arguments.of("ssn", "078-05-1120"),
                Arguments.of("cardNumber", "4111111111111111"),
                Arguments.of("cvv", "123"),
                Arguments.of("pin", "4821"),
                Arguments.of("email", "someone@example.org"),
                Arguments.of("note", "contact me at someone@example.org"),
                Arguments.of("data", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.sig-sig-sig"),
                Arguments.of("blob", "AKIAIOSFODNN7EXAMPLE"),
                Arguments.of("pem", "-----BEGIN PRIVATE KEY-----MIIE"),
                Arguments.of("misc", "token=abcdef0123456789"));
    }

    @ParameterizedTest(name = "{0} is never retained")
    @MethodSource("corpus")
    void secretNeverSurvives(String name, String value) {
        Map<String, Object> out = service.redactParameters(Map.of(name, new String[]{value}), Set.of());
        String serialised = service.toJson(out);
        assertThat(serialised).doesNotContain(value);
    }
}
