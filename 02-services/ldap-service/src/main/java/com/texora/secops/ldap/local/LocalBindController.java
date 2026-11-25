package com.texora.secops.ldap.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * DEV-ONLY local stub for the LDAP bind endpoint.
 *
 * <p>Active only when Spring profile "local" is present. Accepts any non-blank
 * credential for the single test user and returns a fixed dcUserId so that
 * SSO's {@code LdapBindClient} can complete the login flow locally without a
 * real LDAP engine. Must be removed or replaced before pilot.
 *
 * <p>The production bind endpoint will be a proper mTLS-protected endpoint
 * backed by {@code LdapEngineAdapter}. This stub exists only to unblock local
 * development (Known Issues: "LDAP has no /api/v1/bind endpoint but SSO
 * calls it").
 */
@Profile("local")
@RestController
public class LocalBindController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalBindController.class);

    // Single test user for local development only.
    private static final String TEST_USERNAME = "testuser";
    private static final UUID FIXED_DC_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000099");

    @PostMapping("/api/v1/bind")
    public ResponseEntity<?> bind(@RequestBody Map<String, String> body) {
        String username   = body.getOrDefault("username",   "");
        String credential = body.getOrDefault("credential", "");

        if (TEST_USERNAME.equals(username) && !credential.isBlank()) {
            LOGGER.info("LocalBindController: bind succeeded for username={} (dev stub)", username);
            return ResponseEntity.ok(Map.of("dcUserId", FIXED_DC_USER_ID.toString()));
        }

        LOGGER.warn("LocalBindController: bind rejected for username={} (dev stub)", username);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "BIND_FAILED"));
    }
}