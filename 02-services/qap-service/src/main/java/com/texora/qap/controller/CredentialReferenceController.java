package com.texora.qap.controller;

import com.texora.qap.domain.QapCredentialReference;
import com.texora.qap.dto.CredentialReferenceResponse;
import com.texora.qap.dto.RegisterCredentialRequest;
import com.texora.qap.repository.QapCredentialReferenceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Credential references: only the SEC secret reference ID is stored,
 * NEVER actual credential material.
 */
@RestController
@RequestMapping("/api/v1/credentials")
@Tag(name = "Credentials", description = "Register SEC secret references (no credential material stored)")
public class CredentialReferenceController {

    private final QapCredentialReferenceRepository credentialReferenceRepository;

    public CredentialReferenceController(QapCredentialReferenceRepository credentialReferenceRepository) {
        this.credentialReferenceRepository = credentialReferenceRepository;
    }

    @PostMapping
    @Operation(summary = "Register a credential reference (SEC secret ref only)")
    public ResponseEntity<CredentialReferenceResponse> register(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody RegisterCredentialRequest request) {
        QapCredentialReference ref = new QapCredentialReference(UUID.randomUUID(), tenantId, request.getProductId(), request.getSecSecretReferenceId(), request.getDescription(), Instant.now());
        ref.setId(UUID.randomUUID());
        ref.setTenantId(tenantId);
        ref.setProductId(request.getProductId());
        ref.setSecSecretReferenceId(request.getSecSecretReferenceId());
        ref.setDescription(request.getDescription());
        ref.setCreatedAt(Instant.now());
        credentialReferenceRepository.save(ref);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(ref));
    }

    @GetMapping
    @Operation(summary = "List credential references for tenant")
    public ResponseEntity<Page<CredentialReferenceResponse>> list(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Pageable pageable) {
        Page<QapCredentialReference> page = credentialReferenceRepository.findByTenantId(tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    private CredentialReferenceResponse toResponse(QapCredentialReference r) {
        CredentialReferenceResponse dto = new CredentialReferenceResponse();
        dto.setId(r.getId());
        dto.setTenantId(r.getTenantId());
        dto.setProductId(r.getProductId());
        dto.setDescription(r.getDescription());
        dto.setCreatedAt(r.getCreatedAt());
        // secSecretReferenceId intentionally NOT exposed in response
        return dto;
    }
}
