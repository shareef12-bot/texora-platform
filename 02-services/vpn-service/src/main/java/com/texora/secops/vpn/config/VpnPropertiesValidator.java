package com.texora.secops.vpn.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with trigger settings that would silently break the revocation guarantee in either direction.
 * (Defaults and rationale: docs/UNCERTAINTY-TRIGGERS.md.)
 */
@Component
public class VpnPropertiesValidator {

    private final VpnProperties properties;

    public VpnPropertiesValidator(VpnProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        SessionProperties s = properties.getSession();
        SecProperties sec = properties.getSec();
        require(sec.getTimeoutMs() >= 200 && sec.getTimeoutMs() <= 10_000, "vpn.sec.timeout-ms must be 200..10000");
        require(sec.getRevalidationAttempts() >= 1 && sec.getRevalidationAttempts() <= 5, "vpn.sec.revalidation-attempts must be 1..5");
        require(sec.getRevalidationDeadlineMs() >= sec.getTimeoutMs(), "vpn.sec.revalidation-deadline-ms must be >= timeout-ms");
        require(s.getRevalidationIntervalSeconds() >= 15 && s.getRevalidationIntervalSeconds() <= 600,
                "vpn.session.revalidation-interval-seconds must be 15..600");
        // UNDER-triggering guard: the watchdog horizon must be finite and tied to the interval.
        require(s.getMaxUnverifiedSeconds() <= 6 * s.getRevalidationIntervalSeconds(),
                "vpn.session.max-unverified-seconds must be <= 6x the re-validation interval (stale sessions would survive)");
        // OVER-triggering guard: the watchdog must tolerate at least two missed cycles before revoking.
        require(s.getMaxUnverifiedSeconds() >= 2 * s.getRevalidationIntervalSeconds() + s.getClaimLeaseSeconds(),
                "vpn.session.max-unverified-seconds must be >= 2x interval + claim lease (would revoke healthy sessions)");
        require(s.getIdentityStalenessSeconds() >= 30 && s.getIdentityStalenessSeconds() <= 3600,
                "vpn.session.identity-staleness-seconds must be 30..3600");
        require(s.getMaxMinutes() >= 1 && s.getMaxMinutes() <= 1440, "vpn.session.max-minutes must be 1..1440");
        require(s.getClaimLeaseSeconds() >= 10, "vpn.session.claim-lease-seconds must be >= 10");
        require(s.getEstablishGraceSeconds() >= 10, "vpn.session.establish-grace-seconds must be >= 10");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid VPN configuration: " + message);
        }
    }
}
