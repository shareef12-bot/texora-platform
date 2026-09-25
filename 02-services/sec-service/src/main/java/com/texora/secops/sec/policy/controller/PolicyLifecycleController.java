package com.texora.secops.sec.policy.controller;

import com.texora.secops.sec.policy.domain.SecPolicy;
import com.texora.secops.sec.policy.domain.SecPolicyVersion;
import com.texora.secops.sec.policy.service.PolicyLifecycleService;
import com.texora.secops.sec.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * GET/POST /api/v1/security-policy-lifecycle
 * POST     /api/v1/security-policy-lifecycle/{id}/activate
 */
@RestController
@RequestMapping("/api/v1/security-policy-lifecycle")
public class PolicyLifecycleController {

    private final PolicyLifecycleService lifecycleService;

    public PolicyLifecycleController(PolicyLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sec.policy.read')")
    public ResponseEntity<Page<SecPolicy>> listPolicies(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(lifecycleService.listPolicies(tenantId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sec.policy.write')")
    public ResponseEntity<SecPolicy> createPolicy(
            @Valid @RequestBody CreatePolicyRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = TenantContext.requireTenantId();
        SecPolicy policy = lifecycleService.createPolicy(
                tenantId, request.getName(), request.getSubjectType(),
                request.getResource(), request.getAction(), request.getOwnerTeam());
        return ResponseEntity.status(HttpStatus.CREATED).body(policy);
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('sec.policy.write')")
    public ResponseEntity<SecPolicyVersion> createVersion(
            @PathVariable UUID id,
            @Valid @RequestBody CreateVersionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = TenantContext.requireTenantId();
        UUID authoredBy = UUID.fromString(jwt.getSubject());
        SecPolicyVersion version = lifecycleService.createPolicyVersion(
                tenantId, id, request.getEffect(), request.getConditions(), authoredBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(version);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('sec.policy.activate')")
    public ResponseEntity<SecPolicyVersion> activateVersion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = TenantContext.requireTenantId();
        UUID activatedBy = UUID.fromString(jwt.getSubject());
        SecPolicyVersion version = lifecycleService.activateVersion(tenantId, id, activatedBy);
        return ResponseEntity.ok(version);
    }

    // ------------------------------------------------------------------
    // Request DTOs (inner classes — no Lombok)
    // ------------------------------------------------------------------

    public static class CreatePolicyRequest {
        @NotBlank private String name;
        @NotBlank private String subjectType;
        @NotBlank private String resource;
        @NotBlank private String action;
        @NotBlank private String ownerTeam;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSubjectType() { return subjectType; }
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getOwnerTeam() { return ownerTeam; }
        public void setOwnerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; }
    }

    public static class CreateVersionRequest {
        @NotBlank private String effect;
        private Map<String, Object> conditions;

        public String getEffect() { return effect; }
        public void setEffect(String effect) { this.effect = effect; }
        public Map<String, Object> getConditions() { return conditions; }
        public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }
    }
}
