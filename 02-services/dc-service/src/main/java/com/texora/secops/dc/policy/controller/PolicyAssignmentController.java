package com.texora.secops.dc.policy.controller;

import com.texora.secops.dc.dto.DtoMapper;
import com.texora.secops.dc.policy.dto.PolicyAssignmentRequest;
import com.texora.secops.dc.policy.dto.PolicyAssignmentResponse;
import com.texora.secops.dc.policy.service.PolicyAssignmentService;
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

/** Strictest role (dc.policy.assign). Always audited. */
@RestController
@RequestMapping("/api/v1/policy-assignment")
public class PolicyAssignmentController {

    private final PolicyAssignmentService service;

    public PolicyAssignmentController(PolicyAssignmentService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresRole("dc.policy.assign")
    public ResponseEntity<PolicyAssignmentResponse> assign(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                                           @Valid @RequestBody PolicyAssignmentRequest request) {
        PolicyAssignmentResponse body = DtoMapper.assignment(
                service.assign(caller, request.getPolicyId(), request.getSubjectType(), request.getSubjectId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
