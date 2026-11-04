package com.texora.secops.ldap.bind.controller;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.ldap.bind.dto.BindConfigRequest;
import com.texora.secops.ldap.bind.dto.BindConfigResponse;
import com.texora.secops.ldap.bind.service.SecureBindConfigService;
import com.texora.secops.ldap.security.RbacInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/secure-bind-configuration")
public class BindConfigController {

    private final SecureBindConfigService bindConfigService;

    public BindConfigController(SecureBindConfigService bindConfigService) {
        this.bindConfigService = bindConfigService;
    }

    @GetMapping
    public ResponseEntity<List<BindConfigResponse>> list(HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        return ResponseEntity.ok(bindConfigService.listConfigs(claims.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BindConfigResponse> get(@PathVariable UUID id, HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        return ResponseEntity.ok(bindConfigService.getConfig(id, claims.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<BindConfigResponse> create(@RequestBody BindConfigRequest body,
                                                      HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        BindConfigResponse resp = bindConfigService.createConfig(body, claims.getSubject(), claims.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BindConfigResponse> update(@PathVariable UUID id,
                                                      @RequestBody BindConfigRequest body,
                                                      HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        return ResponseEntity.ok(bindConfigService.updateConfig(id, body, claims.getSubject(), claims.getTenantId()));
    }
}
