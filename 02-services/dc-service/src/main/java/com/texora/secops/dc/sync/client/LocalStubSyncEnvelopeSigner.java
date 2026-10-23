package com.texora.secops.dc.sync.client;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * ##########  LOCAL STUB - NOT A REAL KEY MANAGEMENT SOLUTION  ##########
 *
 * <p>Uses the standard JCA HmacSHA256 (no custom cryptography, HLD Guardrail #2) with an EPHEMERAL random key
 * generated at startup - same pattern as the ephemeral JWK signing key in sso-service. Consequences: the key is
 * lost on restart and the LDAP service cannot verify signatures across restarts or between DC replicas.
 * It exists only so the envelope/idempotency/retry/DLQ machinery can be built and tested.
 *
 * <p>Replace with a SEC/KMS-backed {@link SyncEnvelopeSigner} (set dc.sync.signer=sec) once SEC exposes it.
 */
@Component
@ConditionalOnProperty(name = "dc.sync.signer", havingValue = "stub", matchIfMissing = true)
public class LocalStubSyncEnvelopeSigner implements SyncEnvelopeSigner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStubSyncEnvelopeSigner.class);
    private static final String KEY_ID = "ephemeral-local-stub";

    private final Environment environment;
    private final byte[] key = new byte[32];

    public LocalStubSyncEnvelopeSigner(Environment environment) {
        this.environment = environment;
        new SecureRandom().nextBytes(key);
    }

    @PostConstruct
    void warnLoudly() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                throw new IllegalStateException("LocalStubSyncEnvelopeSigner must not run in production (no SEC-backed key yet).");
            }
        }
        LOGGER.warn("################################################################################");
        LOGGER.warn("# SYNC SIGNER IS A LOCAL STUB: ephemeral in-memory HMAC key, NOT from SEC/KMS.     #");
        LOGGER.warn("# LDAP cannot verify these signatures across restarts. Do NOT use in production.   #");
        LOGGER.warn("################################################################################");
    }

    @Override
    public String keyId() {
        return KEY_ID;
    }

    @Override
    public String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}
