package com.texora.secops.cgi.execution.controller;

import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.execution.dto.IsolationConfigRequest;
import com.texora.secops.cgi.execution.dto.IsolationConfigResponse;
import com.texora.secops.cgi.execution.dto.IsolationDecisionRequest;
import com.texora.secops.cgi.execution.service.WorkerIsolationConfigService;
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

/** /api/v1/worker-isolation - cgi.worker.read / cgi.worker.write; decision needs cgi.worker.approve (added role). */
@RestController
@RequestMapping("/api/v1/worker-isolation")
public class WorkerIsolationController {

    private final WorkerIsolationConfigService service;

    public WorkerIsolationController(WorkerIsolationConfigService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("cgi.worker.read")
    public PageResponse<IsolationConfigResponse> list(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return service.list(caller.getTenantId(), page, size);
    }

    @PostMapping
    @RequiresRole("cgi.worker.write")
    @ResponseStatus(HttpStatus.CREATED)
    public IsolationConfigResponse submit(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                          @Valid @RequestBody IsolationConfigRequest request) {
        return service.submit(caller, request);
    }

    @PostMapping("/{id}/decision")
    @RequiresRole("cgi.worker.approve")
    public IsolationConfigResponse decide(@RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller,
                                          @PathVariable UUID id, @Valid @RequestBody IsolationDecisionRequest request) {
        return service.decide(caller, id, request);
    }
}
