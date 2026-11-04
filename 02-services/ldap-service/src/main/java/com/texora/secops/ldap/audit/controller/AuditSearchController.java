package com.texora.secops.ldap.audit.controller;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.ldap.audit.service.AuditSearchService;
import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.security.RbacInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditSearchController {

    private final AuditSearchService auditSearchService;

    public AuditSearchController(AuditSearchService auditSearchService) {
        this.auditSearchService = auditSearchService;
    }

    @GetMapping("/events")
    public ResponseEntity<Page<LdapAuditEvent>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {

        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        Page<LdapAuditEvent> result = auditSearchService.search(
                claims.getTenantId(), action, targetType, targetId, from, to, page, size);
        return ResponseEntity.ok(result);
    }
}
