package com.texora.secops.sec.config;

import com.texora.secops.sec.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(configRepository.findByTenantId(tenantId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sec.config.write')")
    public ResponseEntity<SecConfigEntry> upsertConfig(
            @Valid @RequestBody ConfigRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = TenantContext.requireTenantId();
        UUID updatedBy = UUID.fromString(jwt.getSubject());

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
