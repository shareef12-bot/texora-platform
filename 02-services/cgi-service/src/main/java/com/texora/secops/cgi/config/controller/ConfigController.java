package com.texora.secops.cgi.config.controller;

import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.config.dto.ConfigRequest;
import com.texora.secops.cgi.config.dto.ConfigResponse;
import com.texora.secops.cgi.config.service.TenantConfigService;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET/POST /api/v1/config - cgi.config.read / cgi.config.write. */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final TenantConfigService service;

    public ConfigController(TenantConfigService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("cgi.config.read")
    public PageResponse<ConfigResponse> list(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller) {
        return service.list(caller.getTenantId());
    }

    @PostMapping
    @RequiresRole("cgi.config.write")
    public ConfigResponse upsert(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                 @Valid @RequestBody ConfigRequest request) {
        return service.upsert(caller, request);
    }
}
