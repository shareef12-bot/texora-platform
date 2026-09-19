package com.texora.secops.iam.token;

import com.texora.secops.starter.exception.PlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenValidatorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    private TokenValidator tokenValidator;

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID SUBJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tokenValidator = new TokenValidator(jwtDecoder);
    }

    @Test
    void validate_returns_claims_for_valid_token() {
        Jwt jwt = buildJwt(
            Map.of(
                "ten",   TENANT_ID.toString(),
                "sub",   SUBJECT_ID.toString(),
                "email", "user@texora.com",
                "roles", List.of("USER_READ", "ADMIN")
            )
        );
        when(jwtDecoder.decode("valid.jwt.token")).thenReturn(jwt);

        TokenClaims claims = tokenValidator.validate("Bearer valid.jwt.token");

        assertEquals(TENANT_ID,        claims.getTenantId());
        assertEquals(SUBJECT_ID,       claims.getSubject());
        assertEquals("user@texora.com", claims.getEmail());
        assertTrue(claims.hasRole("USER_READ"));
        assertTrue(claims.hasRole("ADMIN"));
        assertFalse(claims.hasRole("SUPERADMIN"));
    }

    @Test
    void validate_strips_Bearer_prefix() {
        Jwt jwt = buildJwt(Map.of(
            "ten", TENANT_ID.toString(),
            "sub", SUBJECT_ID.toString()
        ));
        when(jwtDecoder.decode("raw.token")).thenReturn(jwt);

        TokenClaims claims = tokenValidator.validate("Bearer raw.token");
        assertEquals(TENANT_ID, claims.getTenantId());
    }

    @Test
    void validate_accepts_token_without_Bearer_prefix() {
        Jwt jwt = buildJwt(Map.of(
            "ten", TENANT_ID.toString(),
            "sub", SUBJECT_ID.toString()
        ));
        when(jwtDecoder.decode("raw.token")).thenReturn(jwt);

        TokenClaims claims = tokenValidator.validate("raw.token");
        assertEquals(TENANT_ID, claims.getTenantId());
    }

    @Test
    void validate_throws_UnauthenticatedException_for_blank_token() {
        assertThrows(PlatformException.UnauthenticatedException.class,
            () -> tokenValidator.validate(""));
        assertThrows(PlatformException.UnauthenticatedException.class,
            () -> tokenValidator.validate(null));
        assertThrows(PlatformException.UnauthenticatedException.class,
            () -> tokenValidator.validate("   "));
    }

    @Test
    void validate_throws_UnauthenticatedException_when_decoder_throws() {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("expired"));

        assertThrows(PlatformException.UnauthenticatedException.class,
            () -> tokenValidator.validate("Bearer expired.jwt.token"));
    }

    @Test
    void validate_throws_when_tenantId_claim_missing() {
        Jwt jwt = buildJwt(Map.of("sub", SUBJECT_ID.toString())); // no "ten"
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        PlatformException.UnauthenticatedException ex = assertThrows(
            PlatformException.UnauthenticatedException.class,
            () -> tokenValidator.validate("Bearer x"));

        assertEquals("MISSING_CLAIM", ex.getCode());
    }

    @Test
    void validate_throws_when_tenantId_not_a_uuid() {
        Jwt jwt = buildJwt(Map.of(
            "ten", "not-a-uuid",
            "sub", SUBJECT_ID.toString()
        ));
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        PlatformException.UnauthenticatedException ex = assertThrows(
            PlatformException.UnauthenticatedException.class,
            () -> tokenValidator.validate("Bearer x"));

        assertEquals("INVALID_CLAIM", ex.getCode());
    }

    @Test
    void validate_returns_empty_roles_when_claim_absent() {
        Jwt jwt = buildJwt(Map.of(
            "ten", TENANT_ID.toString(),
            "sub", SUBJECT_ID.toString()
        ));
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        TokenClaims claims = tokenValidator.validate("Bearer x");

        assertTrue(claims.getRoles().isEmpty());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Jwt buildJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claims(c -> c.putAll(claims))
            .build();
    }
}
