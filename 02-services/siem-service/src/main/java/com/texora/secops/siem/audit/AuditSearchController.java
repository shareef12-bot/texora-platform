package com.texora.secops.siem.audit;

import com.texora.secops.siem.dto.audit.AuditEventResponse;
import com.texora.secops.siem.dto.common.PageResponse;
import com.texora.secops.siem.security.RequiresRole;
import com.texora.secops.siem.security.CallerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller: maps query params to the service call and back. No business logic (B.3). */
@RestController
public class AuditSearchController {

    private final AuditSearchService auditSearchService;

    public AuditSearchController(AuditSearchService auditSearchService) {
        this.auditSearchService = auditSearchService;
    }

    @GetMapping("/api/v1/audit")
    @RequiresRole("siem.audit.read")
    public PageResponse<AuditEventResponse> search(@RequestParam(required = false) String action,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int pageSize) {
        return auditSearchService.search(CallerContext.currentTenantId(), action, page, pageSize);
    }
}
