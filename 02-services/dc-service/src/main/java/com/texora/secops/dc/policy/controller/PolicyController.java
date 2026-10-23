package com.texora.secops.dc.policy.controller;

import com.texora.secops.dc.dto.DtoMapper;
import com.texora.secops.dc.policy.dto.PolicyRequest;
import com.texora.secops.dc.policy.dto.PolicyResponse;
import com.texora.secops.dc.policy.service.PolicyManagementService;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.RbacInterceptor;
import com.texora.secops.dc.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyManagementService service;

    public PolicyController(PolicyManagementService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresRole("dc.policy.write")
    public ResponseEntity<PolicyResponse> create(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                                 @Valid @RequestBody PolicyRequest request) {
        PolicyResponse body = DtoMapper.policy(service.create(caller, request.getSubjectType(), request.getResource(),
                request.getAction(), request.getEffect()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
