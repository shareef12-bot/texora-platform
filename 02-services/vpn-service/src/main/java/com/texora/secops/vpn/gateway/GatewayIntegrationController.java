package com.texora.secops.vpn.gateway;

import com.texora.secops.vpn.dto.GatewayEndpointRequest;
import com.texora.secops.vpn.dto.GatewayEndpointResponse;
import com.texora.secops.vpn.dto.GatewayIntegrationStatus;
import com.texora.secops.vpn.security.Caller;
import com.texora.secops.vpn.security.CallerResolver;
import com.texora.secops.vpn.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin side of gateway integration (SSO token + RBAC). Dataplane-facing callbacks are in {@link DataplaneCallbackController}. */
@RestController
@RequestMapping(value = "/api/v1/gateway-integration", produces = MediaType.APPLICATION_JSON_VALUE)
public class GatewayIntegrationController {

    private final GatewayEndpointService service;
    private final CallerResolver callers;

    public GatewayIntegrationController(GatewayEndpointService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @GetMapping
    @RequiresRole("vpn.gateway.read")
    public GatewayIntegrationStatus status() {
        return service.status(callers.current().getTenantId());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("vpn.gateway.write")
    public GatewayEndpointResponse configure(@Valid @RequestBody GatewayEndpointRequest request) {
        Caller caller = callers.current();
        return service.create(caller.getTenantId(), caller.getUserId(), request);
    }
}
