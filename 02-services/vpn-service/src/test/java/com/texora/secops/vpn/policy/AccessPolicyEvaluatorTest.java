package com.texora.secops.vpn.policy;

import com.texora.secops.vpn.VpnConstants;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyEvaluatorTest {

    private final AccessPolicyEvaluator evaluator = new AccessPolicyEvaluator();
    // Friday 2026-10-16 10:00 UTC
    private final Instant friday10Utc = Instant.parse("2026-10-16T10:00:00Z");

    private AccessPolicyConditions allow() {
        AccessPolicyConditions c = new AccessPolicyConditions();
        c.setEffect(VpnConstants.EFFECT_ALLOW);
        return c;
    }

    private TimeWindowCondition window(String zone, String start, String end, String... days) {
        TimeWindowCondition w = new TimeWindowCondition();
        w.setZone(zone);
        w.setStartTime(start);
        w.setEndTime(end);
        if (days.length > 0) {
            w.setDaysOfWeek(List.of(days));
        }
        return w;
    }

    @Test
    void nullConditionsAreAmbiguousNeverAllow() {
        assertThat(evaluator.evaluateRequested(null, friday10Utc, true, true).getVerdict()).isEqualTo(PolicyVerdict.AMBIGUOUS);
    }

    @Test
    void missingEffectIsAmbiguous() {
        assertThat(evaluator.evaluateRequested(new AccessPolicyConditions(), friday10Utc, true, true).getVerdict())
                .isEqualTo(PolicyVerdict.AMBIGUOUS);
    }

    @Test
    void unknownEffectIsAmbiguous() {
        AccessPolicyConditions c = allow();
        c.setEffect("PERMIT");
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getVerdict()).isEqualTo(PolicyVerdict.AMBIGUOUS);
    }

    @Test
    void denyEffectPolicyDenies() {
        AccessPolicyConditions c = allow();
        c.setEffect(VpnConstants.EFFECT_DENY);
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getVerdict()).isEqualTo(PolicyVerdict.DENY);
    }

    @Test
    void secureDefaultsRequireMfaAndDevice() {
        AccessPolicyConditions c = allow(); // flags null => required
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, false).getReasonCode()).isEqualTo("MFA_NOT_SATISFIED");
        assertThat(evaluator.evaluateRequested(c, friday10Utc, false, true).getReasonCode()).isEqualTo("DEVICE_NOT_ENROLLED");
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getVerdict()).isEqualTo(PolicyVerdict.ALLOW);
    }

    @Test
    void explicitlyOptionalMfaAndDeviceAreHonoured() {
        AccessPolicyConditions c = allow();
        c.setMfaRequired(Boolean.FALSE);
        c.setDeviceEnrollmentRequired(Boolean.FALSE);
        assertThat(evaluator.evaluateRequested(c, friday10Utc, false, false).getVerdict()).isEqualTo(PolicyVerdict.ALLOW);
    }

    @Test
    void insideAndOutsideTimeWindow() {
        AccessPolicyConditions c = allow();
        c.setTimeWindow(window("UTC", "09:00", "18:00"));
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getVerdict()).isEqualTo(PolicyVerdict.ALLOW);
        assertThat(evaluator.evaluateRequested(c, Instant.parse("2026-10-16T19:00:00Z"), true, true).getReasonCode())
                .isEqualTo("OUTSIDE_TIME_WINDOW");
        // end is exclusive
        assertThat(evaluator.evaluateRequested(c, Instant.parse("2026-10-16T18:00:00Z"), true, true).getVerdict())
                .isEqualTo(PolicyVerdict.DENY);
    }

    @Test
    void windowThatWrapsMidnight() {
        AccessPolicyConditions c = allow();
        c.setTimeWindow(window("UTC", "22:00", "06:00"));
        assertThat(evaluator.evaluateRequested(c, Instant.parse("2026-10-16T23:30:00Z"), true, true).getVerdict()).isEqualTo(PolicyVerdict.ALLOW);
        assertThat(evaluator.evaluateRequested(c, Instant.parse("2026-10-16T03:00:00Z"), true, true).getVerdict()).isEqualTo(PolicyVerdict.ALLOW);
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getVerdict()).isEqualTo(PolicyVerdict.DENY);
    }

    @Test
    void daysOfWeekAreEvaluatedInTheWindowZone() {
        AccessPolicyConditions c = allow();
        c.setTimeWindow(window("UTC", "00:00", "23:59", "MONDAY"));
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getReasonCode()).isEqualTo("OUTSIDE_TIME_WINDOW");
        c.setTimeWindow(window("UTC", "00:00", "23:59", "FRIDAY"));
        assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getVerdict()).isEqualTo(PolicyVerdict.ALLOW);
    }

    @Test
    void invalidWindowIsAmbiguousNotAllow() {
        for (TimeWindowCondition w : List.of(
                window("Not/AZone", "09:00", "18:00"),
                window("UTC", "9am", "18:00"),
                window("UTC", "09:00", "09:00"),
                window(null, "09:00", "18:00"),
                window("UTC", "09:00", "18:00", "FUNDAY"))) {
            AccessPolicyConditions c = allow();
            c.setTimeWindow(w);
            assertThat(evaluator.evaluateRequested(c, friday10Utc, true, true).getVerdict())
                    .as("window %s", w).isEqualTo(PolicyVerdict.AMBIGUOUS);
        }
    }

    @Test
    void denyOverrideAppliesOnlyToDenyPoliciesThatApplyNow() {
        AccessPolicyConditions allowPolicy = allow();
        assertThat(evaluator.evaluateDenyOverride(allowPolicy, friday10Utc).getVerdict()).isEqualTo(PolicyVerdict.NOT_APPLICABLE);

        AccessPolicyConditions deny = allow();
        deny.setEffect(VpnConstants.EFFECT_DENY);
        assertThat(evaluator.evaluateDenyOverride(deny, friday10Utc).getVerdict()).isEqualTo(PolicyVerdict.DENY);

        deny.setTimeWindow(window("UTC", "09:00", "18:00"));
        assertThat(evaluator.evaluateDenyOverride(deny, friday10Utc).getVerdict()).isEqualTo(PolicyVerdict.DENY);
        assertThat(evaluator.evaluateDenyOverride(deny, Instant.parse("2026-10-16T20:00:00Z")).getVerdict())
                .isEqualTo(PolicyVerdict.NOT_APPLICABLE);

        deny.setTimeWindow(window("Bad/Zone", "09:00", "18:00"));
        assertThat(evaluator.evaluateDenyOverride(deny, friday10Utc).getVerdict()).isEqualTo(PolicyVerdict.AMBIGUOUS);
        assertThat(evaluator.evaluateDenyOverride(null, friday10Utc).getVerdict()).isEqualTo(PolicyVerdict.AMBIGUOUS);
    }
}
