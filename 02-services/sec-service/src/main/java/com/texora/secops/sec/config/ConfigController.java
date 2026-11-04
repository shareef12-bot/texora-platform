package com.texora.secops.sec.config;

import com.texora.secops.audit.annotation.Audited;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.sec.security.RbacInterceptor;
import com.texora.secops.starter.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GET/POST /api/v1/config
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final SecConfigRepository configRepository;

    public ConfigController(SecConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sec.config.read')")
    public ResponseEntity<Page<SecConfigEntry>> listConfig(Pageable pageable) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(configRepository.findByTenantId(tenantId, pageable));
    }

    // NOTE: this mutation was previously NOT audited at all (no service layer, so no aspect matched).
    // It still writes via the repository from the controller (a B.3 layering deviation, left as-is).
    @Audited(action = "CONFIG_UPSERTED", targetType = "SEC_CONFIG")
    @PostMapping
    @PreAuthorize("hasAuthority('sec.config.write')")
    public ResponseEntity<SecConfigEntry> upsertConfig(
            @Valid @RequestBody ConfigRequest request,
            @RequestAttribute(RbacInterceptor.CLAIMS_ATTRIBUTE) TokenClaims claims) {

        UUID tenantId = TenantContext.get();
        UUID updatedBy = claims.getSubject();

        SecConfigEntry entry = configRepository
                .findByTenantIdAndConfigKey(tenantId, request.getConfigKey())
                .orElseGet(() -> new SecConfigEntry(
                        UUID.randomUUID(), tenantId, request.getConfigKey(),
                        request.getConfigValue(), request.getDescription(), updatedBy));

        entry.setConfigValue(request.getConfigValue());
        entry.setDescription(request.getDescription());
        entry.setUpdatedBy(updatedBy);
        entry.setUpdatedAt(java.time.Instant.now());

        SecConfigEntry saved = configRepository.save(entry);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    public static class ConfigRequest {
        @NotBlank private String configKey;
        @NotBlank private String configValue;
        private String description;

        public String getConfigKey() { return configKey; }
        public void setConfigKey(String configKey) { this.configKey = configKey; }
        public String getConfigValue() { return configValue; }
        public void setConfigValue(String configValue) { this.configValue = configValue; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
