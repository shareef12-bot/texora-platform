package com.texora.secops.vpn.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VpnPropertiesValidatorTest {

    @Test
    void defaultsAreValid() {
        assertThatCode(() -> new VpnPropertiesValidator(new VpnProperties()).validate()).doesNotThrowAnyException();
    }

    @Test
    void horizonTooLongMeansStaleSessionsCouldSurvive() {
        VpnProperties p = new VpnProperties();
        p.getSession().setMaxUnverifiedSeconds(3600);
        assertThatThrownBy(() -> new VpnPropertiesValidator(p).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("stale sessions would survive");
    }

    @Test
    void horizonTooShortMeansHealthySessionsWouldBeRevoked() {
        VpnProperties p = new VpnProperties();
        p.getSession().setMaxUnverifiedSeconds(61);
        assertThatThrownBy(() -> new VpnPropertiesValidator(p).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("would revoke healthy sessions");
    }

    @Test
    void secTimeoutAndAttemptsAreBounded() {
        VpnProperties p = new VpnProperties();
        p.getSec().setTimeoutMs(10);
        assertThatThrownBy(() -> new VpnPropertiesValidator(p).validate()).isInstanceOf(IllegalStateException.class);
        VpnProperties q = new VpnProperties();
        q.getSec().setRevalidationAttempts(50);
        assertThatThrownBy(() -> new VpnPropertiesValidator(q).validate()).isInstanceOf(IllegalStateException.class);
        VpnProperties r = new VpnProperties();
        r.getSec().setRevalidationDeadlineMs(100);
        assertThatThrownBy(() -> new VpnPropertiesValidator(r).validate()).isInstanceOf(IllegalStateException.class);
    }
}
