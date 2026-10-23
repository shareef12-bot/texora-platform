package com.texora.secops.vpn.session;

import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.dto.RevokeSessionRequest;
import com.texora.secops.vpn.dto.RevokeSessionResponse;
import com.texora.secops.vpn.dto.SessionSummary;
import com.texora.secops.vpn.security.CallerResolver;
import com.texora.secops.vpn.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/session-revocation", produces = MediaType.APPLICATION_JSON_VALUE)
public class SessionRevocationController {

    private final SessionControlService service;
    private final CallerResolver callers;

    public SessionRevocationController(SessionControlService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @GetMapping
    @RequiresRole("vpn.session.read")
    public PageResponse<SessionSummary> list(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return service.list(callers.current().getTenantId(), status, page, size);
    }

    @PostMapping(value = "/{sessionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresRole("vpn.session.revoke")
    public RevokeSessionResponse revoke(@PathVariable UUID sessionId, @Valid @RequestBody RevokeSessionRequest request) {
        return service.revokeByAdmin(callers.current(), sessionId, request.getReason());
    }
}
