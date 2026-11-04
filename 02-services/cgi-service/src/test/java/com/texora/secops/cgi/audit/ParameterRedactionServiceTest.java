package com.texora.secops.cgi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.cgi.audit.service.ParameterRedactionService;
import com.texora.secops.cgi.config.props.RedactionProperties;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterRedactionServiceTest {

    private final ParameterRedactionService service = new ParameterRedactionService(new RedactionProperties(), new ObjectMapper());

    private static Map<String, String[]> params(String k, String... v) {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    @Test
    void sensitiveNamesAreRedacted() {
        Map<String, Object> out = service.redactParameters(params("password", "hunter2"), Set.of());
        assertThat(out.get("password")).isEqualTo(List.of(ParameterRedactionService.REDACTED));
    }

    @Test
    void harmlessValuesPassThrough() {
        Map<String, Object> out = service.redactParameters(params("report", "daily"), Set.of());
        assertThat(out.get("report")).isEqualTo(List.of("daily"));
    }

    @Test
    void secretShapedValuesAreRedactedEvenUnderInnocentNames() {
        assertThat(service.redactValue("eyJhbGciOi.eyJzdWIiOiIx.abc123")).isEqualTo(ParameterRedactionService.REDACTED);
        assertThat(service.redactValue("4111 1111 1111 1111")).isEqualTo(ParameterRedactionService.REDACTED);
        assertThat(service.redactValue("bob@example.com")).isEqualTo(ParameterRedactionService.REDACTED);
        assertThat(service.redactValue("password=hunter2")).isEqualTo(ParameterRedactionService.REDACTED);
        assertThat(service.redactValue("123-45-6789")).isEqualTo(ParameterRedactionService.REDACTED);
    }

    @Test
    void nonLuhnDigitStringsAreNotTreatedAsCards() {
        assertThat(service.redactValue("1234567890123")).isEqualTo("1234567890123");
    }

    @Test
    void overlongValuesKeepNoPrefix() {
        String out = service.redactValue("a".repeat(300));
        assertThat(out).isEqualTo("[TRUNCATED len=300]");
    }

    @Test
    void tenantExtraKeysOnlyAddRules() {
        Map<String, Object> out = service.redactParameters(params("employeeBadge", "B-1"), Set.of("employeeBadge"));
        assertThat(out.get("employeeBadge")).isEqualTo(List.of(ParameterRedactionService.REDACTED));
        Map<String, Object> still = service.redactParameters(params("password", "x"), Set.of("employeeBadge"));
        assertThat(still.get("password")).isEqualTo(List.of(ParameterRedactionService.REDACTED));
    }

    @Test
    void secretInsideParameterNameIsRedacted() {
        Map<String, Object> out = service.redactParameters(params("bob@example.com", "x"), Set.of());
        assertThat(out.keySet()).noneMatch(k -> k.contains("bob@"));
    }

    @Test
    void parameterCountIsCapped() {
        Map<String, String[]> many = new LinkedHashMap<>();
        for (int i = 0; i < 150; i++) {
            many.put("p" + i, new String[]{"v"});
        }
        Map<String, Object> out = service.redactParameters(many, Set.of());
        assertThat(out).containsKey("_truncatedParameters");
    }

    @Test
    void internalFaultDiscardsEverythingInsteadOfLeaking() {
        Map<String, String[]> exploding = new AbstractMap<>() {
            @Override
            public Set<Entry<String, String[]>> entrySet() {
                throw new IllegalStateException("boom");
            }
        };
        Map<String, Object> out = service.redactParameters(exploding, Set.of());
        assertThat(out).containsOnlyKeys("_redaction");
    }

    @Test
    void payloadMapsAreRedactedRecursively() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("token", "abc");
        inner.put("ok", "fine");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nested", inner);
        payload.put("list", List.of("x@y.com", "plain"));
        Map<String, Object> out = service.redactMap(payload);
        assertThat(out.toString()).doesNotContain("abc").doesNotContain("x@y.com").contains("fine").contains("plain");
    }

    @Test
    void toJsonProducesJson() {
        assertThat(service.toJson(Map.of("a", "b"))).isEqualTo("{\"a\":\"b\"}");
    }
}
