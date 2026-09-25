package com.texora.secops.sec.secrets.controller;

import com.texora.secops.sec.secrets.domain.SecKeyRotationRecord;
import com.texora.secops.sec.secrets.service.KeyRotationService;
import com.texora.secops.sec.security.TenantContext;
import jakarta.validation.Valid;
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

import java.time.Instant;
import java.util.UUID;

/**
 * GET/POST /api/v1/key-rotation-metadata
 */
@RestController
@RequestMapping("/api/v1/key-rotation-metadata")
public class KeyRotationController {

    private final KeyRotationService keyRotationService;

    public KeyRotationController(KeyRotationService keyRotationService) {
        this.keyRotationService = keyRotationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sec.key.read')")
    public ResponseEntity<Page<SecKeyRotationRecord>> listRotationRecords(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(keyRotationService.listRotationRecords(tenantId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sec.key.write')")
    public ResponseEntity<SecKeyRotationRecord> scheduleRotation(
            @Valid @RequestBody ScheduleRotationRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        SecKeyRotationRecord record = keyRotationService.scheduleRotation(
                tenantId, request.getSecretReferenceId(), request.getRotationDueAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(record);
    }

    @PostMapping("/{id}/outcome")
    @PreAuthorize("hasAuthority('sec.key.write')")
    public ResponseEntity<SecKeyRotationRecord> recordOutcome(
            @PathVariable UUID id,
            @Valid @RequestBody OutcomeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID rotatedBy = UUID.fromString(jwt.getSubject());
        SecKeyRotationRecord record = keyRotationService.recordRotationOutcome(
                tenantId, id, rotatedBy, request.getOutcome());
        return ResponseEntity.ok(record);
    }

    // ------------------------------------------------------------------
    // Request DTOs
    // ------------------------------------------------------------------

    public static class ScheduleRotationRequest {
        @NotNull private UUID secretReferenceId;
        @NotNull private Instant rotationDueAt;

        public UUID getSecretReferenceId() { return secretReferenceId; }
        public void setSecretReferenceId(UUID secretReferenceId) { this.secretReferenceId = secretReferenceId; }
        public Instant getRotationDueAt() { return rotationDueAt; }
        public void setRotationDueAt(Instant rotationDueAt) { this.rotationDueAt = rotationDueAt; }
    }

    public static class OutcomeRequest {
        private String outcome;

        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
    }
}
