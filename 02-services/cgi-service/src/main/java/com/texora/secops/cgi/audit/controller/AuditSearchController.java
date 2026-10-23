package com.texora.secops.cgi.audit.controller;

import com.texora.secops.cgi.audit.dto.AuditEventResponse;
import com.texora.secops.cgi.audit.service.AuditSearchService;
import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.security.RequiresRole;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** GET /api/v1/audit - search audit records (redacted). Tenant comes from the token, never a parameter. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditSearchController {

    private final AuditSearchService auditSearchService;

    public AuditSearchController(AuditSearchService auditSearchService) {
        this.auditSearchService = auditSearchService;
    }

    @GetMapping
    @RequiresRole("cgi.audit.read")
    public PageResponse<AuditEventResponse> search(
            @RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditSearchService.search(caller.getTenantId(), action, outcome, from, to, page, size);
    }
}
