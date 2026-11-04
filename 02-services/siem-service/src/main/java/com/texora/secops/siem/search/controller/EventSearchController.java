package com.texora.secops.siem.search.controller;

import com.texora.secops.siem.dto.common.PageResponse;
import com.texora.secops.siem.dto.search.NormalizedEventDto;
import com.texora.secops.siem.search.service.TenantFilteredSearchService;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.siem.security.RequiresRole;
import com.texora.secops.siem.security.CallerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Thin controller (B.3). {@code tenantScope} is accepted so a caller CAN pass it (matching
 *  their own tenant is a no-op; anything else is rejected by the service with 403 - see
 *  TenantFilteredSearchServiceTest for the adversarial case this exists to cover). */
@RestController
public class EventSearchController {

    private final TenantFilteredSearchService searchService;

    public EventSearchController(TenantFilteredSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/events/search")
    @RequiresRole("siem.search.read")
    public PageResponse<NormalizedEventDto> search(@RequestParam(required = false) UUID tenantScope,
                                                     @RequestParam(required = false) String eventType,
                                                     @RequestParam(required = false) String sourceService,
                                                     @RequestParam(required = false) String productId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int pageSize) {
        TokenClaims caller = CallerContext.get();
        return searchService.search(caller.getTenantId(), caller.getSubject(), tenantScope,
                eventType, sourceService, productId, page, pageSize);
    }
}
