package com.texora.secops.dc.config.controller;

import com.texora.secops.dc.config.dto.ConfigDecisionRequest;
import com.texora.secops.dc.config.dto.ConfigResponse;
import com.texora.secops.dc.config.dto.ConfigSubmitRequest;
import com.texora.secops.dc.config.service.ConfigApprovalService;
import com.texora.secops.dc.dto.DtoMapper;
import com.texora.secops.dc.dto.PageResponse;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.RbacInterceptor;
import com.texora.secops.dc.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final ConfigApprovalService service;

    public ConfigController(ConfigApprovalService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("dc.config.read")
    public PageResponse<ConfigResponse> list(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return DtoMapper.page(service.list(caller.getTenantId(), status,
                DtoMapper.pageable(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"))), DtoMapper::config);
    }

    @PostMapping
    @RequiresRole("dc.config.write")
    public ResponseEntity<ConfigResponse> submit(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                                 @Valid @RequestBody ConfigSubmitRequest request) {
        ConfigResponse body = DtoMapper.config(service.submit(caller, request.getKey(), request.getValue()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/decision")
    @RequiresRole("dc.config.approve")
    public ConfigResponse decide(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                 @PathVariable("id") UUID id,
                                 @Valid @RequestBody ConfigDecisionRequest request) {
        return DtoMapper.config(service.decide(caller, id, request.getDecision(), request.getReason()));
    }
}
