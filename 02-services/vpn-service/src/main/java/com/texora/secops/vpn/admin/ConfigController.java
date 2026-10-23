package com.texora.secops.vpn.admin;

import com.texora.secops.vpn.dto.ConfigEntryRequest;
import com.texora.secops.vpn.dto.ConfigEntryResponse;
import com.texora.secops.vpn.security.Caller;
import com.texora.secops.vpn.security.CallerResolver;
import com.texora.secops.vpn.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/config", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConfigController {

    private final VpnConfigService service;
    private final CallerResolver callers;

    public ConfigController(VpnConfigService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @GetMapping
    @RequiresRole("vpn.config.read")
    public List<ConfigEntryResponse> list() {
        return service.list(callers.current().getTenantId());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresRole("vpn.config.write")
    public ConfigEntryResponse set(@Valid @RequestBody ConfigEntryRequest request) {
        Caller caller = callers.current();
        return service.set(caller.getTenantId(), caller.getUserId(), request.getKey(), request.getValue());
    }
}
