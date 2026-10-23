package com.texora.qap.execution.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redacts sensitive field names from captured request/response JSON before
 * any persistence (QAP Requirement 4 — same pattern as CGI Gateway's
 * ParameterRedactionService).
 *
 * <p>Any field whose name matches a known-sensitive key is replaced with
 * {@code "[REDACTED]"}.  The set of sensitive keys should be treated as a
 * living list — extend it whenever new field types are captured.
 *
 * <p>Redaction happens BEFORE any data reaches the database or logs.
 * If parsing fails the entire value is replaced with {@code "[REDACTED-PARSE-ERROR]"}.
 */
@Component
public class PayloadRedactor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadRedactor.class);

    static final Set<String> SENSITIVE_KEYS = Set.of(
        "password", "passwd", "secret", "token", "access_token", "refresh_token",
        "authorization", "credential", "credentials", "apiKey", "api_key",
        "sessionToken", "session_token", "x-api-key", "bearerToken",
        "ssn", "socialSecurityNumber", "creditCard", "cardNumber",
        "cvv", "pin", "privateKey", "private_key"
    );

    private static final Set<String> SENSITIVE_KEYS_LOWER = SENSITIVE_KEYS.stream()
            .map(k -> k.toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final ObjectMapper objectMapper;

    public PayloadRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Redact the given JSON string.
     *
     * @param json input JSON, may be null or blank
     * @return redacted JSON string, or null if input was null
     */
    public String redact(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            redactNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            LOGGER.warn("PayloadRedactor: failed to parse JSON for redaction; replacing entire value");
            return "[REDACTED-PARSE-ERROR]";
        }
    }

    private void redactNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fields().forEachRemaining(entry -> {
                if (SENSITIVE_KEYS_LOWER.contains(entry.getKey().toLowerCase(java.util.Locale.ROOT))) {
                    obj.put(entry.getKey(), "[REDACTED]");
                } else {
                    redactNode(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::redactNode);
        }
        // scalars — nothing to redact
    }
}
