package com.texora.secops.ftp.endpoint.controller;

import com.texora.secops.ftp.dto.EndpointRequest;
import com.texora.secops.ftp.dto.EndpointResponse;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.endpoint.service.EndpointAllowlistService;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/endpoints")
public class EndpointAllowlistController {

    private final EndpointAllowlistService service;

    public EndpointAllowlistController(EndpointAllowlistService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("ftp.endpoint.read")
    public PageResponse<EndpointResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return service.list(CallerAuthentication.require().getTenantId(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("ftp.endpoint.write")
    public EndpointResponse register(@Valid @RequestBody EndpointRequest request) {
        CallerAuthentication caller = CallerAuthentication.require();
        return service.register(caller.getTenantId(), caller.getSubject(), caller.getActorId(), request);
    }
}
