package com.texora.secops.ftp.admin.controller;

import com.texora.secops.ftp.admin.service.ServiceConfigService;
import com.texora.secops.ftp.dto.ConfigEntryRequest;
import com.texora.secops.ftp.dto.ConfigEntryResponse;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/config")
public class ServiceConfigController {

    private final ServiceConfigService service;

    public ServiceConfigController(ServiceConfigService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("ftp.config.read")
    public List<ConfigEntryResponse> list() {
        return service.list(CallerAuthentication.require().getTenantId());
    }

    @PostMapping
    @RequiresRole("ftp.config.write")
    public ConfigEntryResponse upsert(@Valid @RequestBody ConfigEntryRequest request) {
        CallerAuthentication caller = CallerAuthentication.require();
        return service.upsert(caller.getTenantId(), caller.getSubject(), caller.getActorId(), request);
    }
}
