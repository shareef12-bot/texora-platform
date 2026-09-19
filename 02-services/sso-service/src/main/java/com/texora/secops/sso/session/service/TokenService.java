package com.texora.secops.sso.session.service;

import com.texora.secops.sso.audit.annotation.Audited;
import com.texora.secops.sso.domain.SsoToken;
import com.texora.secops.sso.domain.TokenType;
import com.texora.secops.sso.repository.SsoTokenRepository;
import com.texora.secops.sso.session.cache.RedisSessionCache;
import com.texora.secops.sso.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues/refreshes/revokes access, refresh, and ID tokens. Token-signing
 * itself is Spring Security's job (OAuth2 Authorization Server / SAML2,
 * using the signing key referenced via SEC) — this service manages the
 * sso_token lifecycle rows and the Redis mirror, never raw key material.
 */
@Service
public class TokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration ID_TOKEN_TTL = Duration.ofMinutes(15);

    private final SsoTokenRepository tokenRepository;
    private final RedisSessionCache redisSessionCache;

    public TokenService(SsoTokenRepository tokenRepository, RedisSessionCache redisSessionCache) {
        this.tokenRepository = tokenRepository;
        this.redisSessionCache = redisSessionCache;
    }

    @Transactional
    public List<SsoToken> issueTokenSet(UUID sessionId) {
        UUID tenantId = TenantContext.get();
        Instant now = Instant.now();

        SsoToken access = save(tenantId, sessionId, TokenType.ACCESS, now, now.plus(ACCESS_TOKEN_TTL));
        SsoToken refresh = save(tenantId, sessionId, TokenType.REFRESH, now, now.plus(REFRESH_TOKEN_TTL));
        SsoToken id = save(tenantId, sessionId, TokenType.ID, now, now.plus(ID_TOKEN_TTL));

        return List.of(access, refresh, id);
    }

    private SsoToken save(UUID tenantId, UUID sessionId, TokenType type, Instant issuedAt, Instant expiresAt) {
        SsoToken token = new SsoToken(UUID.randomUUID(), tenantId, sessionId, type, issuedAt, expiresAt, null);
        tokenRepository.save(token);
        redisSessionCache.putToken(token.getId(), type.name(), expiresAt);
        return token;
    }

    @Audited(action = "session.revoke", targetType = "sso_token")
    @Transactional
    public int revokeAllForSession(UUID sessionId) {
        UUID tenantId = TenantContext.get();
        List<SsoToken> tokens = tokenRepository.findAllBySessionIdAndTenantId(sessionId, tenantId);
        Instant now = Instant.now();
        int revoked = 0;
        for (SsoToken token : tokens) {
            if (!token.isRevoked()) {
                token.setRevokedAt(now);
                tokenRepository.save(token);
                redisSessionCache.evictToken(token.getId());
                revoked++;
            }
        }
        return revoked;
    }
}
