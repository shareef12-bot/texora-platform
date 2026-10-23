package com.texora.secops.vpn.policy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Strictly-parsed condition document. Unknown fields are rejected on submission; unparseable stored JSON is AMBIGUOUS -> DENY.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessPolicyConditions {

    private String effect;

    private Boolean mfaRequired;

    private Boolean deviceEnrollmentRequired;

    private TimeWindowCondition timeWindow;

    private Integer maxSessionMinutes;

    public AccessPolicyConditions() {
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public Boolean getMfaRequired() {
        return mfaRequired;
    }

    public void setMfaRequired(Boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    public Boolean getDeviceEnrollmentRequired() {
        return deviceEnrollmentRequired;
    }

    public void setDeviceEnrollmentRequired(Boolean deviceEnrollmentRequired) {
        this.deviceEnrollmentRequired = deviceEnrollmentRequired;
    }

    public TimeWindowCondition getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(TimeWindowCondition timeWindow) {
        this.timeWindow = timeWindow;
    }

    public Integer getMaxSessionMinutes() {
        return maxSessionMinutes;
    }

    public void setMaxSessionMinutes(Integer maxSessionMinutes) {
        this.maxSessionMinutes = maxSessionMinutes;
    }

    @Override
    public String toString() {
        return "AccessPolicyConditions{" + "effect=" + effect + ", mfaRequired=" + mfaRequired + ", deviceEnrollmentRequired=" + deviceEnrollmentRequired + ", timeWindow=" + timeWindow + ", maxSessionMinutes=" + maxSessionMinutes + "}";
    }

}
