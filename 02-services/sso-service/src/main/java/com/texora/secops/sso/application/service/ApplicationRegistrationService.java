package com.texora.secops.sso.application.service;

import com.texora.secops.sso.audit.annotation.Audited;
import com.texora.secops.sso.domain.ApplicationStatus;
import com.texora.secops.sso.domain.Protocol;
import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.domain.SsoOidcSamlConfig;
import com.texora.secops.sso.dto.ApplicationRegistrationRequest;
import com.texora.secops.sso.exception.ConflictException;
import com.texora.secops.sso.exception.NotFoundException;
import com.texora.secops.sso.repository.SsoApplicationRepository;
import com.texora.secops.sso.repository.SsoOidcSamlConfigRepository;
import com.texora.secops.sso.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registers relying-party applications (SSO-F-007). Per ADR D-3, product_id
 * must be one of the five canonical values (ilmora, texora-jobs, taskorbit,
 * hrms, crm) — one sso_application row per product per tenant.
 */
@Service
public class ApplicationRegistrationService {

    private final SsoApplicationRepository applicationRepository;
    private final SsoOidcSamlConfigRepository configRepository;

    public ApplicationRegistrationService(SsoApplicationRepository applicationRepository,
                                           SsoOidcSamlConfigRepository configRepository) {
        this.applicationRepository = applicationRepository;
        this.configRepository = configRepository;
    }

    @Audited(action = "application.register", targetType = "sso_application")
    @Transactional
    public SsoApplication register(ApplicationRegistrationRequest request, UUID registeredBy) {
        UUID tenantId = TenantContext.get();
        String clientId = request.getProductId() + "-" + UUID.randomUUID();

        if (applicationRepository.existsByClientId(clientId)) {
            throw new ConflictException("client_id already registered: " + clientId);
        }

        SsoApplication application = new SsoApplication(UUID.randomUUID(), tenantId, clientId,
                request.getName(), request.getProductId(), ApplicationStatus.ACTIVE, registeredBy,
                Instant.now());
        applicationRepository.save(application);

        SsoOidcSamlConfig config = new SsoOidcSamlConfig(UUID.randomUUID(), tenantId, application.getId(),
                request.getProtocol(), String.join(",", request.getRedirectUris()), "sec://pending");
        configRepository.save(config);

        return application;
    }

    public List<SsoApplication> list() {
        return applicationRepository.findAllByTenantId(TenantContext.get());
    }

    public SsoApplication get(UUID id) {
        return applicationRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new NotFoundException("Application not found: " + id));
    }
}
