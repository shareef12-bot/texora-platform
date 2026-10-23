package com.texora.secops.vpn.gateway;

import com.texora.secops.vpn.dto.DataplaneEventRequest;
import com.texora.secops.vpn.dto.DataplaneEventResponse;
import com.texora.secops.vpn.security.MtlsOnly;
import com.texora.secops.vpn.session.SessionControlService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dataplane-facing endpoint. Authenticated by mTLS client certificate (allow-listed subject), NOT an SSO token.
 * The dataplane can only tell us a tunnel has ended; it can never extend, create or re-activate anything.
 */
@RestController
@RequestMapping(value = "/api/v1/gateway-integration/dataplane-events", produces = MediaType.APPLICATION_JSON_VALUE)
public class DataplaneCallbackController {

    private final SessionControlService sessions;

    public DataplaneCallbackController(SessionControlService sessions) {
        this.sessions = sessions;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @MtlsOnly
    public DataplaneEventResponse onEvent(@Valid @RequestBody DataplaneEventRequest request) {
        boolean accepted = sessions.onDataplaneTunnelTerminated(request.getDataplaneSessionRef());
        DataplaneEventResponse response = new DataplaneEventResponse();
        response.setAccepted(accepted);
        return response;
    }
}
