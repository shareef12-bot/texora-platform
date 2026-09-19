package com.texora.secops.sso.session.service;

import com.texora.secops.sso.audit.annotation.Audited;
import com.texora.secops.sso.domain.SessionStatus;
import com.texora.secops.sso.domain.SsoSession;
import com.texora.secops.sso.exception.NotFoundException;
import com.texora.secops.sso.repository.SsoSessionRepository;
import com.texora.secops.sso.session.cache.RedisSessionCache;
import com.texora.secops.sso.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Creates/expires/revokes sso_session rows, coordinating with the Redis mirror. */
@Service
public class SessionService {

    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final SsoSessionRepository sessionRepository;
    private final RedisSessionCache redisSessionCache;

    public SessionService(SsoSessionRepository sessionRepository, RedisSessionCache redisSessionCache) {
        this.sessionRepository = sessionRepository;
        this.redisSessionCache = redisSessionCache;
    }

    @Transactional
    public SsoSession createActiveSession(UUID dcUserId, UUID applicationId) {
        UUID tenantId = TenantContext.get();
        Instant now = Instant.now();
        SsoSession session = new SsoSession(UUID.randomUUID(), tenantId, dcUserId, applicationId,
                SessionStatus.ACTIVE, now, now.plus(SESSION_TTL));
        sessionRepository.save(session);
        redisSessionCache.putSession(session.getId(), SessionStatus.ACTIVE.name(), session.getExpiresAt());
        return session;
    }

    public List<SsoSession> listSessions() {
        return sessionRepository.findAllByTenantId(TenantContext.get());
    }

    public SsoSession getSession(UUID sessionId) {
        return sessionRepository.findByIdAndTenantId(sessionId, TenantContext.get())
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    /**
     * Revokes a session immediately: matching Redis entries are invalidated
     * first (so validation calls stop trusting it right away), then the
     * durable row is marked REVOKED. The audit event for this action is
     * emitted by the caller (SessionTokenController via TokenService /
     * this method's @Audited annotation) regardless of outcome, per LLD §6.2.
     */
    @Audited(action = "session.revoke", targetType = "sso_session")
    @Transactional
    public SsoSession revokeSession(UUID sessionId) {
        SsoSession session = getSession(sessionId);
        redisSessionCache.evictSession(sessionId);
        session.setStatus(SessionStatus.REVOKED);
        sessionRepository.save(session);
        return session;
    }
}
