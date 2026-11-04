package com.texora.secops.ftp.admin.controller;

import com.texora.secops.ftp.admin.service.PolicyService;
import com.texora.secops.ftp.dto.PolicySummary;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("ftp.policy.read")
    public List<PolicySummary> list() {
        return service.list(CallerAuthentication.require().getTenantId());
    }
}
