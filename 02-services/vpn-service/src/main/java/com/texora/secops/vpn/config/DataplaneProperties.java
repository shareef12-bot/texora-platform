package com.texora.secops.vpn.config;

import java.util.ArrayList;
import java.util.List;

/**
 * allowedClientSubjects: RFC2253 subject DNs of dataplane client certificates. Empty list means every dataplane callback is rejected (fail closed).
 */
public class DataplaneProperties {

    private List<String> allowedClientSubjects = new ArrayList<>();

    private int healthCheckSeconds = 15;

    public DataplaneProperties() {
    }

    public List<String> getAllowedClientSubjects() {
        return allowedClientSubjects;
    }

    public void setAllowedClientSubjects(List<String> allowedClientSubjects) {
        this.allowedClientSubjects = allowedClientSubjects;
    }

    public int getHealthCheckSeconds() {
        return healthCheckSeconds;
    }

    public void setHealthCheckSeconds(int healthCheckSeconds) {
        this.healthCheckSeconds = healthCheckSeconds;
    }

}
