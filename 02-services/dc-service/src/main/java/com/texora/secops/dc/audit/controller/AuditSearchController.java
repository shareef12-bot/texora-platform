package com.texora.secops.dc.audit.controller;

import com.texora.secops.dc.audit.dto.AuditEventResponse;
import com.texora.secops.dc.audit.service.AuditSearchService;
import com.texora.secops.dc.dto.DtoMapper;
import com.texora.secops.dc.dto.PageResponse;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.RbacInterceptor;
import com.texora.secops.dc.security.RequiresRole;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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
    @RequiresRole("dc.audit.read")
    public PageResponse<AuditEventResponse> search(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                                   @RequestParam(required = false) UUID actorId,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return DtoMapper.page(service.search(caller.getTenantId(), actorId, action, from, to,
                DtoMapper.pageable(page, size, Sort.unsorted())), DtoMapper::audit);
    }
}
