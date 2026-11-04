package com.texora.secops.vpn.audit;

import com.texora.secops.vpn.dto.AuditEventResponse;
import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.security.Caller;
import com.texora.secops.vpn.security.CallerResolver;
import com.texora.secops.vpn.security.RequiresRole;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/audit", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuditSearchController {

    private final AuditSearchService service;
    private final CallerResolver callers;

    public AuditSearchController(AuditSearchService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @GetMapping
    @RequiresRole("vpn.audit.read")
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Caller caller = callers.current();
        return service.search(caller.getTenantId(), from, to, action, actorId, targetType, targetId, page, size);
    }
}
