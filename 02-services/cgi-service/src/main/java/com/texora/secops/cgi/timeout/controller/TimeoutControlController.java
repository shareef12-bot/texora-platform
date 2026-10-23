package com.texora.secops.cgi.timeout.controller;

import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.security.RequiresRole;
import com.texora.secops.cgi.timeout.dto.TimeoutControlRequest;
import com.texora.secops.cgi.timeout.dto.TimeoutControlResponse;
import com.texora.secops.cgi.timeout.service.TimeoutControlService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET/POST /api/v1/execution-timeout-controls - cgi.timeout.read / cgi.timeout.write. */
@RestController
@RequestMapping("/api/v1/execution-timeout-controls")
public class TimeoutControlController {

    private final TimeoutControlService service;

    public TimeoutControlController(TimeoutControlService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("cgi.timeout.read")
    public PageResponse<TimeoutControlResponse> list(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return service.list(caller.getTenantId(), page, size);
    }

    @PostMapping
    @RequiresRole("cgi.timeout.write")
    public TimeoutControlResponse upsert(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                         @Valid @RequestBody TimeoutControlRequest request) {
        return service.upsert(caller, request);
    }
}
