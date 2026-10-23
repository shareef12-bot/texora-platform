package com.texora.secops.vpn.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.vpn.VpnConstants;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses and validates policy conditions with a STRICT mapper (Spring Boot's default mapper ignores unknown fields,
 * which would let a typo like "mfaRequried" silently weaken a policy).
 *
 * Conditions document:
 * <pre>
 * { "effect": "ALLOW"|"DENY",            default ALLOW
 *   "mfaRequired": true|false,            default TRUE  (secure default)
 *   "deviceEnrollmentRequired": true|false, default TRUE (secure default)
 *   "maxSessionMinutes": 1..1440,
 *   "timeWindow": { "zone": "Asia/Kolkata", "startTime": "09:00", "endTime": "18:00",
 *                   "daysOfWeek": ["MONDAY", ...] } }
 * </pre>
 */
@Component
public class PolicyConditionsCodec {

    private final ObjectMapper strict = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    /** Validate user input and return the normalized document (defaults made explicit). */
    public AccessPolicyConditions fromMap(Map<String, Object> input) {
        if (input == null) {
            throw new PolicyConditionsException("conditions are required");
        }
        AccessPolicyConditions parsed;
        try {
            parsed = strict.convertValue(input, AccessPolicyConditions.class);
        } catch (IllegalArgumentException e) {
            throw new PolicyConditionsException("conditions contain unknown or mistyped fields", e);
        }
        return normalizeAndValidate(parsed);
    }

    /** Parse stored JSON. Any failure is surfaced so the caller can treat the policy as AMBIGUOUS (-> DENY). */
    public AccessPolicyConditions parseStored(String json) {
        if (json == null || json.isBlank()) {
            throw new PolicyConditionsException("stored conditions are empty");
        }
        try {
            return normalizeAndValidate(strict.readValue(json, AccessPolicyConditions.class));
        } catch (JsonProcessingException e) {
            throw new PolicyConditionsException("stored conditions are not parseable", e);
        }
    }

    public String toJson(AccessPolicyConditions conditions) {
        try {
            return strict.writeValueAsString(conditions);
        } catch (JsonProcessingException e) {
            throw new PolicyConditionsException("conditions could not be serialized", e);
        }
    }

    public Map<String, Object> toMap(String json) {
        try {
            return strict.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            Map<String, Object> broken = new LinkedHashMap<>();
            broken.put("error", "stored conditions unreadable");
            return broken;
        }
    }

    private AccessPolicyConditions normalizeAndValidate(AccessPolicyConditions c) {
        if (c == null) {
            throw new PolicyConditionsException("conditions are empty");
        }
        if (c.getEffect() == null) {
            c.setEffect(VpnConstants.EFFECT_ALLOW);
        }
        if (!VpnConstants.EFFECT_ALLOW.equals(c.getEffect()) && !VpnConstants.EFFECT_DENY.equals(c.getEffect())) {
            throw new PolicyConditionsException("effect must be ALLOW or DENY");
        }
        if (c.getMfaRequired() == null) {
            c.setMfaRequired(Boolean.TRUE);
        }
        if (c.getDeviceEnrollmentRequired() == null) {
            c.setDeviceEnrollmentRequired(Boolean.TRUE);
        }
        if (c.getMaxSessionMinutes() != null && (c.getMaxSessionMinutes() < 1 || c.getMaxSessionMinutes() > 1440)) {
            throw new PolicyConditionsException("maxSessionMinutes must be between 1 and 1440");
        }
        TimeWindowCondition w = c.getTimeWindow();
        if (w != null) {
            if (w.getZone() == null || w.getStartTime() == null || w.getEndTime() == null) {
                throw new PolicyConditionsException("timeWindow requires zone, startTime and endTime");
            }
            try {
                ZoneId.of(w.getZone());
                LocalTime start = LocalTime.parse(w.getStartTime());
                LocalTime end = LocalTime.parse(w.getEndTime());
                if (start.equals(end)) {
                    throw new PolicyConditionsException("timeWindow startTime and endTime must differ");
                }
                if (w.getDaysOfWeek() != null) {
                    for (String day : w.getDaysOfWeek()) {
                        DayOfWeek.valueOf(day);
                    }
                }
            } catch (DateTimeException | IllegalArgumentException e) {
                throw new PolicyConditionsException("timeWindow has an invalid zone, time (HH:mm) or day name", e);
            }
        }
        return c;
    }
}
