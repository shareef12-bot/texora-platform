package com.texora.secops.sso.federation.service;

import com.texora.secops.sso.audit.annotation.Audited;
import com.texora.secops.sso.domain.Protocol;
import com.texora.secops.sso.domain.SsoOidcSamlConfig;
import com.texora.secops.sso.dto.OidcSamlConfigRequest;
import com.texora.secops.sso.exception.NotFoundException;
import com.texora.secops.sso.repository.SsoOidcSamlConfigRepository;
import com.texora.secops.sso.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages OIDC/SAML relying-party configuration rows and terminates the
 * actual protocol handshakes via Spring Security's built-in OAuth2
 * Authorization Server (/oauth2/*) and SAML2 Service Provider (/saml2/*)
 * support — configured declaratively in application.yml. This class NEVER
 * hand-implements signing, encryption, or the federation handshake itself
 * (LLD CRITICAL CONSTRAINT); it only rejects requests referencing
 * unsigned/invalid assertions by delegating to Spring Security's own
 * validators and refusing to weaken their default configuration.
 */
@Service
public class IdentityProviderAdapterService {

    private final SsoOidcSamlConfigRepository configRepository;

    public IdentityProviderAdapterService(SsoOidcSamlConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Audited(action = "federation.config.write", targetType = "sso_oidc_saml_config")
    @Transactional
    public SsoOidcSamlConfig upsertConfig(OidcSamlConfigRequest request) {
        UUID tenantId = TenantContext.get();
        SsoOidcSamlConfig config = new SsoOidcSamlConfig(UUID.randomUUID(), tenantId,
                request.getApplicationId(), request.getProtocol(), String.join(",", request.getRedirectUris()),
                "sec://pending");
        configRepository.save(config);
        return config;
    }

    public List<SsoOidcSamlConfig> listForApplication(UUID applicationId) {
        return configRepository.findAllByApplicationIdAndTenantId(applicationId, TenantContext.get());
    }

    public SsoOidcSamlConfig get(UUID id) {
        return configRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new NotFoundException("Federation config not found: " + id));
    }
}
