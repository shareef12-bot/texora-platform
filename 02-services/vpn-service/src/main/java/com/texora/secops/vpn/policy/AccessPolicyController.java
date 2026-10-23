package com.texora.secops.vpn.policy;

import com.texora.secops.vpn.dto.AccessPolicyRequest;
import com.texora.secops.vpn.dto.AccessPolicyResponse;
import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.dto.PolicyAssignmentRequest;
import com.texora.secops.vpn.dto.PolicyAssignmentResponse;
import com.texora.secops.vpn.dto.PolicyDecisionRequest;
import com.texora.secops.vpn.dto.PolicyDecisionResponse;
import com.texora.secops.vpn.dto.SecurityPolicyView;
import com.texora.secops.vpn.security.CallerResolver;
import com.texora.secops.vpn.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** REST only; all logic lives in {@link PolicyApprovalService}. */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AccessPolicyController {

    private final PolicyApprovalService service;
    private final CallerResolver callers;

    public AccessPolicyController(PolicyApprovalService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @GetMapping("/api/v1/access-policies")
    @RequiresRole("vpn.policy.read")
    public PageResponse<AccessPolicyResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return service.list(callers.current().getTenantId(), page, size);
    }

    @PostMapping(value = "/api/v1/access-policies", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("vpn.policy.write")
    public AccessPolicyResponse submit(@Valid @RequestBody AccessPolicyRequest request) {
        return service.submit(callers.current(), request);
    }

    @PostMapping(value = "/api/v1/access-policies/{id}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresRole("vpn.policy.approve")
    public PolicyDecisionResponse decide(@PathVariable UUID id, @Valid @RequestBody PolicyDecisionRequest request) {
        return service.decide(callers.current(), id, request);
    }

    @PostMapping("/api/v1/access-policies/{id}/disable")
    @RequiresRole("vpn.policy.approve")
    public AccessPolicyResponse disable(@PathVariable UUID id) {
        return service.disable(callers.current(), id);
    }

    @PostMapping(value = "/api/v1/access-policies/{id}/assignments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("vpn.policy.assign")
    public PolicyAssignmentResponse assign(@PathVariable UUID id, @Valid @RequestBody PolicyAssignmentRequest request) {
        return service.assign(callers.current(), id, request.getSubjectId());
    }

    @GetMapping("/api/v1/policies")
    @RequiresRole("vpn.policy.read")
    public List<SecurityPolicyView> effectivePolicies() {
        return service.listEffective(callers.current().getTenantId());
    }
}
