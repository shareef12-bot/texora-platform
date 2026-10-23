package com.texora.secops.cgi.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.cgi.config.props.RedactionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips sensitive values BEFORE anything reaches cgi_request_log, cgi_audit_event or Kafka (CGI_TDD 'Audit').
 *
 * <p>Design: deny-by-pattern on BOTH the parameter name and the value, plus conservative size limits. Coverage
 * gaps are a SILENT leakage risk, so the rules lean toward over-redaction, and any internal fault replaces the
 * whole structure with a failure marker rather than falling back to the raw data.
 * Regression tests: {@code RedactionRegressionTest} - add a row for every handler onboarded.
 */
@Service
public class ParameterRedactionService {

    public static final String REDACTED = "[REDACTED]";

    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterRedactionService.class);

    private static final int MAX_VALUE_LENGTH = 256;
    private static final int MAX_PARAMETERS = 100;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DEPTH = 6;

    /** Key names (normalised: lower-case, alphanumerics only) containing any of these are sensitive. */
    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "password", "passwd", "pwd", "passphrase", "secret", "token", "apikey", "accesskey", "privatekey",
            "authorization", "credential", "cookie", "session", "bearer", "signature", "jwt", "creditcard",
            "cardnumber", "cardno", "socialsecurity", "taxid", "passport", "iban", "accountnumber", "routingnumber",
            "email", "phone", "mobile", "birth", "address");

    /** Key names that are sensitive only when they match exactly (the fragment would over-match common words). */
    private static final Set<String> SENSITIVE_KEY_EXACT = Set.of(
            "key", "auth", "pin", "otp", "cvv", "cvc", "ssn", "pan", "dob", "sig", "sas", "mfa", "totp", "nonce", "csrf");

    private static final List<Pattern> SENSITIVE_VALUE_PATTERNS = List.of(
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"),                 // JWT
            Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}"),                                 // auth header value
            Pattern.compile("-----BEGIN [A-Z ]+-----"),                                                         // PEM material
            Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"),                                                   // AWS key id
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}"),                                                   // GitHub token
            Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}"),                                                 // Slack token
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),                                                      // US SSN
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),                                 // e-mail
            Pattern.compile("(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|authorization)\\b\\s*[:=]\\s*\\S+"), // inline k=v
            Pattern.compile("[A-Za-z0-9+/_=-]{32,}"));                                                          // long opaque token

    private static final Pattern CARD_CANDIDATE = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    private final Set<String> configuredKeys;
    private final ObjectMapper objectMapper;

    public ParameterRedactionService(RedactionProperties properties, ObjectMapper objectMapper) {
        this.configuredKeys = normaliseAll(properties == null ? List.of() : properties.getExtraSensitiveKeys());
        this.objectMapper = objectMapper;
    }

    /**
     * Redacts servlet-style parameters. {@code tenantExtraKeys} can only ADD to the built-in rules.
     * Never throws; on any fault returns a marker map containing no input data.
     */
    public Map<String, Object> redactParameters(Map<String, String[]> parameters, Collection<String> tenantExtraKeys) {
        try {
            Set<String> extras = new HashSet<>(configuredKeys);
            extras.addAll(normaliseAll(tenantExtraKeys));
            Map<String, Object> out = new LinkedHashMap<>();
            if (parameters == null) {
                return out;
            }
            int index = 0;
            for (Map.Entry<String, String[]> entry : new TreeMap<>(parameters).entrySet()) {
                if (index >= MAX_PARAMETERS) {
                    out.put("_truncatedParameters", parameters.size() - MAX_PARAMETERS);
                    break;
                }
                String name = safeName(entry.getKey(), index);
                String[] values = entry.getValue() == null ? new String[0] : entry.getValue();
                List<Object> redactedValues = new ArrayList<>();
                if (isSensitiveKey(entry.getKey(), extras)) {
                    redactedValues.add(REDACTED);
                } else {
                    for (String v : values) {
                        redactedValues.add(redactValue(v));
                    }
                }
                out.put(name, redactedValues);
                index++;
            }
            return out;
        } catch (RuntimeException e) {
            LOGGER.error("Parameter redaction failed - discarding ALL parameter data ({})", e.getClass().getName());
            return failureMarker();
        }
    }

    /** Redacts an arbitrary payload map (audit payloads). Never throws; faults yield the failure marker. */
    public Map<String, Object> redactMap(Map<String, Object> payload) {
        try {
            if (payload == null) {
                return new LinkedHashMap<>();
            }
            return redactMapInternal(payload, 0);
        } catch (RuntimeException e) {
            LOGGER.error("Payload redaction failed - discarding ALL payload data ({})", e.getClass().getName());
            return failureMarker();
        }
    }

    /** Value-level redaction. Anything over the length cap is replaced by a length-only marker (no prefix kept). */
    public String redactValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            return "[TRUNCATED len=" + value.length() + "]";
        }
        for (Pattern p : SENSITIVE_VALUE_PATTERNS) {
            if (p.matcher(value).find()) {
                return REDACTED;
            }
        }
        if (containsCardNumber(value)) {
            return REDACTED;
        }
        return value;
    }

    /** JSON text for Kafka. Falls back to the failure marker; never returns raw input. */
    public String toJson(Map<String, Object> redacted) {
        try {
            return objectMapper.writeValueAsString(redacted);
        } catch (JsonProcessingException | RuntimeException e) {
            LOGGER.error("Could not serialise redacted payload ({})", e.getClass().getName());
            return "{\"_redaction\":\"FAILED\"}";
        }
    }

    /** A key that normalises to nothing (e.g. "---") is treated as sensitive: fail closed. */
    public boolean isSensitiveKey(String key, Collection<String> extraNormalisedKeys) {
        if (key == null) {
            return true;
        }
        String n = normalise(key);
        if (n.isEmpty() || SENSITIVE_KEY_EXACT.contains(n)) {
            return true;
        }
        for (String fragment : SENSITIVE_KEY_FRAGMENTS) {
            if (n.contains(fragment)) {
                return true;
            }
        }
        if (extraNormalisedKeys != null) {
            for (String extra : extraNormalisedKeys) {
                if (!extra.isEmpty() && n.contains(extra)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- internals

    private Map<String, Object> redactMapInternal(Map<?, ?> source, int depth) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : source.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (isSensitiveKey(key, configuredKeys)) {
                out.put(truncateName(key), REDACTED);
            } else {
                out.put(truncateName(key), redactAny(e.getValue(), depth + 1));
            }
        }
        return out;
    }

    private Object redactAny(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            return "[MAX_DEPTH]";
        }
        if (value instanceof Map<?, ?> m) {
            return redactMapInternal(m, depth);
        }
        if (value instanceof Collection<?> c) {
            List<Object> list = new ArrayList<>();
            for (Object o : c) {
                list.add(redactAny(o, depth + 1));
            }
            return list;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number) {
            String s = String.valueOf(value);
            return REDACTED.equals(redactValue(s)) ? REDACTED : value;
        }
        return redactValue(String.valueOf(value));
    }

    private String safeName(String name, int index) {
        if (name == null) {
            return "[NULL_NAME_" + index + "]";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "[LONG_NAME_" + index + "]";
        }
        // a parameter NAME can itself carry a secret (e.g. an e-mail or token used as a key)
        return REDACTED.equals(redactValue(name)) ? "[REDACTED_NAME_" + index + "]" : name;
    }

    private static String truncateName(String name) {
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static boolean containsCardNumber(String value) {
        Matcher m = CARD_CANDIDATE.matcher(value);
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhnValid(digits)) {
                return true;
            }
        }
        return false;
    }

    private static boolean luhnValid(String digits) {
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private static String normalise(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Set<String> normaliseAll(Collection<String> keys) {
        Set<String> out = new HashSet<>();
        if (keys != null) {
            for (String k : keys) {
                if (k != null && !normalise(k).isEmpty()) {
                    out.add(normalise(k));
                }
            }
        }
        return out;
    }

    private static Map<String, Object> failureMarker() {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("_redaction", "FAILED");
        return marker;
    }
}
