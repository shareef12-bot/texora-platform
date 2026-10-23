package com.texora.secops.dc.infra;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Single seam for "now" so time-dependent logic is unit-testable. (Deliberately NOT a java.time.Clock bean:
 * the shared starter may already define one and an extra Clock bean would make injection ambiguous.)
 */
@Component
public class TimeSource {

    public Instant now() {
        return Instant.now();
    }
}
