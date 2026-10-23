package com.texora.secops.cgi.route.controller;

import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.route.dto.RouteDecisionRequest;
import com.texora.secops.cgi.route.dto.RouteResponse;
import com.texora.secops.cgi.route.dto.RouteSubmitRequest;
import com.texora.secops.cgi.route.service.RouteApprovalService;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** /api/v1/route-allowlist - cgi.route.read / cgi.route.write / cgi.route.approve. No business logic here. */
@RestController
@RequestMapping("/api/v1/route-allowlist")
public class RouteAllowlistController {

    private final RouteApprovalService service;

    public RouteAllowlistController(RouteApprovalService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("cgi.route.read")
    public PageResponse<RouteResponse> list(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return service.list(caller.getTenantId(), page, size);
    }

    @PostMapping
    @RequiresRole("cgi.route.write")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteResponse submit(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                @Valid @RequestBody RouteSubmitRequest request) {
        return service.submit(caller, request);
    }

    @PostMapping("/{id}/decision")
    @RequiresRole("cgi.route.approve")
    public RouteResponse decide(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                @PathVariable UUID id, @Valid @RequestBody RouteDecisionRequest request) {
        return service.decide(caller, id, request);
    }
}
