package com.texora.secops.vpn.policy;

import com.texora.secops.vpn.VpnConstants;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure, stateless evaluation of one policy's conditions. Anything it cannot decide is AMBIGUOUS, and AMBIGUOUS is
 * never treated as allow by any caller (VPN-F-005, Requirement 1).
 */
@Component
public class AccessPolicyEvaluator {

    /** Evaluate the policy the subject asked to connect under. mfaSatisfied is computed by the caller. */
    public PolicyEvaluation evaluateRequested(AccessPolicyConditions c, Instant now, boolean deviceEnrolled, boolean mfaSatisfied) {
        if (c == null || c.getEffect() == null) {
            return new PolicyEvaluation(PolicyVerdict.AMBIGUOUS, "CONDITIONS_MISSING");
        }
        if (VpnConstants.EFFECT_DENY.equals(c.getEffect())) {
            return new PolicyEvaluation(PolicyVerdict.DENY, "POLICY_EFFECT_DENY");
        }
        if (!VpnConstants.EFFECT_ALLOW.equals(c.getEffect())) {
            return new PolicyEvaluation(PolicyVerdict.AMBIGUOUS, "EFFECT_UNKNOWN");
        }
        if (c.getTimeWindow() != null) {
            Boolean inside = insideWindow(c.getTimeWindow(), now);
            if (inside == null) {
                return new PolicyEvaluation(PolicyVerdict.AMBIGUOUS, "TIME_WINDOW_INVALID");
            }
            if (!inside) {
                return new PolicyEvaluation(PolicyVerdict.DENY, "OUTSIDE_TIME_WINDOW");
            }
        }
        // Null flags mean "required": the secure default.
        if (!Boolean.FALSE.equals(c.getMfaRequired()) && !mfaSatisfied) {
            return new PolicyEvaluation(PolicyVerdict.DENY, "MFA_NOT_SATISFIED");
        }
        if (!Boolean.FALSE.equals(c.getDeviceEnrollmentRequired()) && !deviceEnrolled) {
            return new PolicyEvaluation(PolicyVerdict.DENY, "DEVICE_NOT_ENROLLED");
        }
        return new PolicyEvaluation(PolicyVerdict.ALLOW, "ALLOWED");
    }

    /**
     * For OTHER policies assigned to the subject: a DENY-effect policy that applies right now overrides any ALLOW.
     * ALLOW-effect policies other than the requested one are ignored here.
     */
    public PolicyEvaluation evaluateDenyOverride(AccessPolicyConditions c, Instant now) {
        if (c == null || c.getEffect() == null) {
            return new PolicyEvaluation(PolicyVerdict.AMBIGUOUS, "CONDITIONS_MISSING");
        }
        if (!VpnConstants.EFFECT_DENY.equals(c.getEffect())) {
            return new PolicyEvaluation(PolicyVerdict.NOT_APPLICABLE, "NOT_A_DENY_POLICY");
        }
        if (c.getTimeWindow() == null) {
            return new PolicyEvaluation(PolicyVerdict.DENY, "DENY_POLICY_APPLIES");
        }
        Boolean inside = insideWindow(c.getTimeWindow(), now);
        if (inside == null) {
            return new PolicyEvaluation(PolicyVerdict.AMBIGUOUS, "TIME_WINDOW_INVALID");
        }
        return inside ? new PolicyEvaluation(PolicyVerdict.DENY, "DENY_POLICY_APPLIES")
                : new PolicyEvaluation(PolicyVerdict.NOT_APPLICABLE, "DENY_WINDOW_NOT_ACTIVE");
    }

    /**
     * @return TRUE/FALSE, or null when the window itself is not valid (caller must treat as AMBIGUOUS).
     * daysOfWeek is evaluated against the local calendar day (in the window's zone) of the instant being checked.
     */
    Boolean insideWindow(TimeWindowCondition w, Instant now) {
        if (w.getZone() == null || w.getStartTime() == null || w.getEndTime() == null) {
            return null;
        }
        try {
            ZonedDateTime local = now.atZone(ZoneId.of(w.getZone()));
            LocalTime start = LocalTime.parse(w.getStartTime());
            LocalTime end = LocalTime.parse(w.getEndTime());
            if (start.equals(end)) {
                return null;
            }
            if (w.getDaysOfWeek() != null && !w.getDaysOfWeek().isEmpty()) {
                Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                for (String d : w.getDaysOfWeek()) {
                    days.add(DayOfWeek.valueOf(d));
                }
                if (!days.contains(local.getDayOfWeek())) {
                    return Boolean.FALSE;
                }
            }
            LocalTime t = local.toLocalTime();
            if (start.isBefore(end)) {
                return !t.isBefore(start) && t.isBefore(end);
            }
            return !t.isBefore(start) || t.isBefore(end); // window wraps midnight
        } catch (DateTimeException | IllegalArgumentException e) {
            return null;
        }
    }
}
