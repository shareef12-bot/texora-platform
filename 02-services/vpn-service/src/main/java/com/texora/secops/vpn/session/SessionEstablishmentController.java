package com.texora.secops.vpn.session;

import com.texora.secops.vpn.dto.EstablishSessionRequest;
import com.texora.secops.vpn.dto.SessionResponse;
import com.texora.secops.vpn.security.CallerResolver;
import com.texora.secops.vpn.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A subject requests VPN access (LLD s5.3 step 1). Not in the s6.1 table, but the establishment flow cannot be reached
 * without it. The subject is the SSO token's own subject; it cannot be supplied in the body.
 */
@RestController
@RequestMapping(value = "/api/v1/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
public class SessionEstablishmentController {

    private final SessionControlService service;
    private final CallerResolver callers;

    public SessionEstablishmentController(SessionControlService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("vpn.session.establish")
    public SessionResponse establish(@Valid @RequestBody EstablishSessionRequest request) {
        return service.establish(callers.current(), request);
    }
}
