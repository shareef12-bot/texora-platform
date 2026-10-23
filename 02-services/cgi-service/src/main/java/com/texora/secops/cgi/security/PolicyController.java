package com.texora.secops.cgi.security;

import com.texora.secops.cgi.security.dto.PolicyInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/v1/policies - lists security policies applicable to gateway/route decisions. */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyCatalogService policyCatalogService;

    public PolicyController(PolicyCatalogService policyCatalogService) {
        this.policyCatalogService = policyCatalogService;
    }

    @GetMapping
    @RequiresRole("cgi.policy.read")
    public List<PolicyInfo> list() {
        return policyCatalogService.listPolicies();
    }
}
