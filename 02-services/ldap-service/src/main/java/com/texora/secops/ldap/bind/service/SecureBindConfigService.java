package com.texora.secops.ldap.bind.service;

import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.bind.dto.BindConfigRequest;
import com.texora.secops.ldap.bind.dto.BindConfigResponse;
import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.domain.LdapBindConfig;
import com.texora.secops.ldap.repository.BindConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages protected LDAP bind endpoint configurations.
 *
 * <p>Security notes enforced here:
 * <ul>
 *   <li>TLS is required by default; any attempt to set {@code tlsRequired=false} is rejected.</li>
 *   <li>A null or blank {@code allowedSource} is allowed but logged at WARN — an overly broad
 *       bind config is the primary misconfiguration threat (LLD §9.4).</li>
 *   <li>No credential material (passwords, keys) is stored or logged.</li>
 * </ul>
 */
@Service
public class SecureBindConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecureBindConfigService.class);

    private final BindConfigRepository bindConfigRepository;
    private final LdapAuditHelper auditHelper;

    public SecureBindConfigService(BindConfigRepository bindConfigRepository,
                                    LdapAuditHelper auditHelper) {
        this.bindConfigRepository = bindConfigRepository;
        this.auditHelper = auditHelper;
    }

    @Transactional(readOnly = true)
    public List<BindConfigResponse> listConfigs(UUID tenantId) {
        return bindConfigRepository.findAllByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BindConfigResponse getConfig(UUID id, UUID tenantId) {
        LdapBindConfig cfg = bindConfigRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bind config not found: " + id));
        return toResponse(cfg);
    }

    @Transactional
    public BindConfigResponse createConfig(BindConfigRequest request, UUID createdBy, UUID tenantId) {
        validate(request);

        LdapBindConfig.Status status = parseStatus(request.getStatus(), LdapBindConfig.Status.ACTIVE);

        LdapBindConfig cfg = new LdapBindConfig(
                UUID.randomUUID(), tenantId, request.getBindDn(),
                request.isTlsRequired(), request.getAllowedSource(),
                status, createdBy, Instant.now()
        );
        bindConfigRepository.save(cfg);

        warnIfBroadSource(cfg);

        auditHelper.record(tenantId, createdBy, "BIND_CONFIG_CREATED",
                "LdapBindConfig", cfg.getId().toString(), LdapAuditEvent.Outcome.SUCCESS, null);

        return toResponse(cfg);
    }

    @Transactional
    public BindConfigResponse updateConfig(UUID id, BindConfigRequest request,
                                            UUID updatedBy, UUID tenantId) {
        validate(request);
        LdapBindConfig cfg = bindConfigRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bind config not found: " + id));

        cfg.setBindDn(request.getBindDn());
        cfg.setTlsRequired(request.isTlsRequired());
        cfg.setAllowedSource(request.getAllowedSource());
        if (request.getStatus() != null) {
            cfg.setStatusEnum(parseStatus(request.getStatus(), cfg.getStatusEnum()));
        }
        cfg.setUpdatedBy(updatedBy);
        cfg.setUpdatedAt(Instant.now());
        bindConfigRepository.save(cfg);

        warnIfBroadSource(cfg);

        auditHelper.record(tenantId, updatedBy, "BIND_CONFIG_UPDATED",
                "LdapBindConfig", id.toString(), LdapAuditEvent.Outcome.SUCCESS, null);

        return toResponse(cfg);
    }

    // --- private helpers ---

    private void validate(BindConfigRequest request) {
        if (request.getBindDn() == null || request.getBindDn().isBlank()) {
            throw new IllegalArgumentException("bindDn must not be blank");
        }
        if (!request.isTlsRequired()) {
            throw new IllegalArgumentException(
                    "tlsRequired must be true — anonymous or non-TLS binds are not permitted (LLD §9.1)");
        }
    }

    private void warnIfBroadSource(LdapBindConfig cfg) {
        if (cfg.getAllowedSource() == null || cfg.getAllowedSource().isBlank()) {
            LOGGER.warn("SECURITY: bindDn '{}' has no allowedSource restriction — " +
                        "this is overly broad and is the primary misconfiguration threat (LLD §9.4)",
                        cfg.getBindDn());
        }
    }

    private LdapBindConfig.Status parseStatus(String raw, LdapBindConfig.Status defaultStatus) {
        if (raw == null || raw.isBlank()) {
            return defaultStatus;
        }
        try {
            return LdapBindConfig.Status.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + raw + ". Must be ACTIVE or DISABLED.");
        }
    }

    private BindConfigResponse toResponse(LdapBindConfig cfg) {
        BindConfigResponse r = new BindConfigResponse();
        r.setId(cfg.getId());
        r.setTenantId(cfg.getTenantId());
        r.setBindDn(cfg.getBindDn());
        r.setTlsRequired(cfg.isTlsRequired());
        r.setAllowedSource(cfg.getAllowedSource());
        r.setStatus(cfg.getStatus());
        r.setCreatedBy(cfg.getCreatedBy());
        r.setCreatedAt(cfg.getCreatedAt());
        r.setUpdatedBy(cfg.getUpdatedBy());
        r.setUpdatedAt(cfg.getUpdatedAt());
        return r;
    }
}
