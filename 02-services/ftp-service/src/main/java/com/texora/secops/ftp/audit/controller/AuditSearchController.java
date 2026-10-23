package com.texora.secops.ftp.audit.controller;

import com.texora.secops.ftp.audit.service.AuditSearchService;
import com.texora.secops.ftp.dto.AuditEventResponse;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.RequiresRole;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditSearchController {

    private final AuditSearchService service;

    public AuditSearchController(AuditSearchService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("ftp.audit.read")
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(CallerAuthentication.require().getTenantId(), actorId, action, targetType, targetId,
                from, to, page, size);
    }
}
