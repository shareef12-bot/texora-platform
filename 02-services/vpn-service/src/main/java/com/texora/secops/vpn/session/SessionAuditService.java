package com.texora.secops.vpn.session;

import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.SessionEvent;
import com.texora.secops.vpn.repository.SessionEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Records every session transition twice, atomically: the vpn_session_event lifecycle trace and the immutable
 * vpn_audit_event (which the outbox then retries to Kafka). Joins the caller's transaction so state and audit commit together.
 */
@Service
public class SessionAuditService {

    private final SessionEventRepository events;
    private final VpnAuditRecorder audit;
    private final Clock clock;

    public SessionAuditService(SessionEventRepository events, VpnAuditRecorder audit, Clock clock) {
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /** @param sessionId null for decisions made before any session row exists (denials). */
    @Transactional
    public void record(UUID tenantId, UUID sessionId, UUID actorId, String eventType, String detail,
                       String action, String outcome, String targetId, Map<String, Object> payload) {
        if (sessionId != null) {
            SessionEvent e = new SessionEvent();
            e.setId(UUID.randomUUID());
            e.setTenantId(tenantId);
            e.setSessionId(sessionId);
            e.setEventType(eventType);
            e.setOccurredAt(clock.instant());
            e.setDetail(detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail));
            events.save(e);
        }
        audit.record(tenantId, actorId, action, "VPN_SESSION", sessionId != null ? sessionId.toString() : targetId, outcome, payload);
    }
}
