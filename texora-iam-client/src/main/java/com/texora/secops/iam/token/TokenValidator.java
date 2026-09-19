package com.texora.secops.iam.token;

import com.texora.secops.starter.exception.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates SSO-issued OAuth2/OIDC bearer tokens and extracts platform claims.
 *
 * <p>Uses Spring Security's {@link JwtDecoder} (configured with the SSO JWKS URI)
 * so that signature verification, expiry, issuer, and audience checks are all
 * performed by the framework before this class ever sees the claims.
 *
 * <p><strong>Fail-closed guarantee:</strong> any exception during validation
 * (malformed token, expired, revoked, unknown key, missing required claim) throws
 * {@link PlatformException.UnauthenticatedException}. This method never returns
 * a partial or unvalidated {@link TokenClaims}.
 *
 * <p>Claim mapping:
 * <ul>
 *   <li>{@code ten} — tenant UUID</li>
 *   <li>{@code sub} — subject UUID (standard OIDC claim)</li>
 *   <li>{@code email} — caller email (optional)</li>
 *   <li>{@code roles} — list of role strings</li>
 *   <li>{@code jti} — JWT ID (standard claim)</li>
 * </ul>
 */
public class TokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenValidator.class);

    private static final String CLAIM_TENANT_ID = "ten";
    private static final String CLAIM_ROLES     = "roles";
    private static final String CLAIM_EMAIL     = "email";

    private final JwtDecoder jwtDecoder;

    public TokenValidator(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Validates the raw bearer token string and returns extracted claims.
     *
     * @param rawToken the {@code Authorization: Bearer <token>} value,
     *                 with or without the "Bearer " prefix
     * @return validated, non-null {@link TokenClaims}
     * @throws PlatformException.UnauthenticatedException on any validation failure
     */
    public TokenClaims validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new PlatformException.UnauthenticatedException(
                "MISSING_TOKEN", "Authorization header is absent or blank");
        }

        String token = rawToken.startsWith("Bearer ")
            ? rawToken.substring(7).trim()
            : rawToken.trim();

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            LOGGER.debug("JWT validation failed: {}", ex.getMessage());
            // Do NOT log the raw token — it may contain sensitive claims
            throw new PlatformException.UnauthenticatedException(
                "INVALID_TOKEN", "Bearer token is invalid or expired");
        }

        return extractClaims(jwt);
    }

    private TokenClaims extractClaims(Jwt jwt) {
        UUID tenantId = requireUuidClaim(jwt, CLAIM_TENANT_ID);
        UUID subject  = requireUuidClaim(jwt, "sub");

        String email = jwt.getClaimAsString(CLAIM_EMAIL); // nullable

        Set<String> roles = extractRoles(jwt);

        String jwtId = jwt.getId(); // jti claim — nullable

        return new TokenClaims(tenantId, subject, email, roles, jwtId);
    }

    private UUID requireUuidClaim(Jwt jwt, String claimName) {
        String raw = jwt.getClaimAsString(claimName);
        if (raw == null || raw.isBlank()) {
            LOGGER.warn("JWT missing required claim '{}' — failing closed", claimName);
            throw new PlatformException.UnauthenticatedException(
                "MISSING_CLAIM", "Token is missing required claim: " + claimName);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("JWT claim '{}' is not a valid UUID: '{}' — failing closed",
                claimName, raw);
            throw new PlatformException.UnauthenticatedException(
                "INVALID_CLAIM", "Token claim '" + claimName + "' is not a valid UUID");
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(Jwt jwt) {
        Object rawRoles = jwt.getClaim(CLAIM_ROLES);
        if (rawRoles == null) {
            return Collections.emptySet();
        }
        if (rawRoles instanceof List<?> list) {
            Set<String> result = new HashSet<>();
            for (Object item : list) {
                if (item instanceof String s) {
                    result.add(s);
                }
            }
            return result;
        }
        LOGGER.warn("JWT 'roles' claim is not a list — defaulting to empty roles");
        return Collections.emptySet();
    }
}
