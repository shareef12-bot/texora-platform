package com.texora.secops.sso.session.controller;

import com.texora.secops.sso.domain.SsoSession;
import com.texora.secops.sso.dto.SessionResponse;
import com.texora.secops.sso.dto.SessionRevocationResponse;
import com.texora.secops.sso.session.service.SessionService;
import com.texora.secops.sso.session.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET/POST/DELETE /api/v1/session/token-management — roles sso.session.read
 * / sso.session.revoke. DELETE invalidates matching Redis entries
 * IMMEDIATELY and always emits an audit event, success or failure
 * (SessionService.revokeSession is @Audited).
 */
@RestController
@RequestMapping("/api/v1/session/token-management")
public class SessionTokenController {

    private final SessionService sessionService;
    private final TokenService tokenService;

    public SessionTokenController(SessionService sessionService, TokenService tokenService) {
        this.sessionService = sessionService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> list() {
        List<SessionResponse> body = sessionService.listSessions().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> get(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(toResponse(sessionService.getSession(sessionId)));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<SessionRevocationResponse> revoke(@PathVariable UUID sessionId) {
        SsoSession session = sessionService.revokeSession(sessionId);
        int tokensRevoked = tokenService.revokeAllForSession(sessionId);
        return ResponseEntity.ok(new SessionRevocationResponse(session.getId(), session.getStatus(),
                Instant.now(), tokensRevoked));
    }

    private SessionResponse toResponse(SsoSession session) {
        return new SessionResponse(session.getId(), session.getDcUserId(), session.getApplicationId(),
                session.getStatus(), session.getCreatedAt(), session.getExpiresAt());
    }
}
