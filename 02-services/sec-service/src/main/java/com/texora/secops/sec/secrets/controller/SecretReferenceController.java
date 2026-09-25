package com.texora.secops.sec.secrets.controller;

import com.texora.secops.sec.secrets.domain.SecSecretReference;
import com.texora.secops.sec.secrets.dto.SecretReferenceResponse;
import com.texora.secops.sec.secrets.service.SecretReferenceService;
import com.texora.secops.sec.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GET/POST /api/v1/secret-references
 * GET      /api/v1/secret-references/{logicalName}/resolve (mTLS service auth)
 */
@RestController
@RequestMapping("/api/v1/secret-references")
public class SecretReferenceController {

    private final SecretReferenceService secretReferenceService;

    public SecretReferenceController(SecretReferenceService secretReferenceService) {
        this.secretReferenceService = secretReferenceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sec.secret.read')")
    public ResponseEntity<Page<SecSecretReference>> listReferences(
            @RequestParam String callerService,
            Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(secretReferenceService.listReferences(tenantId, callerService, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sec.secret.write')")
    public ResponseEntity<SecSecretReference> registerReference(
            @Valid @RequestBody RegisterReferenceRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        SecSecretReference reference = secretReferenceService.registerReference(
                tenantId, request.getLogicalName(), request.getVaultPath(),
                request.getKmsKeyId(), request.getOwningService(), request.getClassification());
        return ResponseEntity.status(HttpStatus.CREATED).body(reference);
    }

    /**
     * mTLS-protected endpoint — ServiceAuthInterceptor authenticates the caller.
     * Returns reference (vaultPath + kmsKeyId) ONLY, never material.
     */
    @GetMapping("/{logicalName}/resolve")
    public ResponseEntity<SecretReferenceResponse> resolveReference(
            @PathVariable String logicalName,
            @RequestParam String callerService) {
        UUID tenantId = TenantContext.requireTenantId();
        SecretReferenceResponse response = secretReferenceService.resolveReference(
                tenantId, logicalName, callerService);
        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------
    // Request DTO
    // ------------------------------------------------------------------

    public static class RegisterReferenceRequest {
        @NotBlank private String logicalName;
        @NotBlank private String vaultPath;
        private String kmsKeyId;
        @NotBlank private String owningService;
        private String classification = "CONFIDENTIAL";

        public String getLogicalName() { return logicalName; }
        public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
        public String getVaultPath() { return vaultPath; }
        public void setVaultPath(String vaultPath) { this.vaultPath = vaultPath; }
        public String getKmsKeyId() { return kmsKeyId; }
        public void setKmsKeyId(String kmsKeyId) { this.kmsKeyId = kmsKeyId; }
        public String getOwningService() { return owningService; }
        public void setOwningService(String owningService) { this.owningService = owningService; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
    }
}
