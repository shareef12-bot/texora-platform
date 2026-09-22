package com.texora.secops.sso.session.cache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Redis-backed mirror of active sessions/tokens for low-latency validation
 * (SSO_TDD 'Session Service' row). PostgreSQL remains the durable system of
 * record; this cache is a TTL-aligned accelerator that can be rebuilt from
 * PostgreSQL after a Redis restart.
 */
@Component
public class RedisSessionCache {

    private static final String SESSION_KEY_PREFIX = "sso:session:";
    private static final String TOKEN_KEY_PREFIX = "sso:token:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisSessionCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void putSession(UUID sessionId, String status, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sessionId, status, ttl);
    }

    public void putToken(UUID tokenId, String tokenType, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + tokenId, tokenType, ttl);
    }

    /** Invalidates the session entry immediately — used by session revocation (LLD §6.2). */
    public void evictSession(UUID sessionId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
    }

    public void evictToken(UUID tokenId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + tokenId);
    }

    public boolean isSessionCached(UUID sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_KEY_PREFIX + sessionId));
    }
}
