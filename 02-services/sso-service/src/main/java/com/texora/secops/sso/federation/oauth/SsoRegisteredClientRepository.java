package com.texora.secops.sso.federation.oauth;

import com.texora.secops.sso.domain.ApplicationStatus;
import com.texora.secops.sso.domain.Protocol;
import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.domain.SsoOidcSamlConfig;
import com.texora.secops.sso.repository.SsoApplicationRepository;
import com.texora.secops.sso.repository.SsoOidcSamlConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Backs Spring's OAuth2 Authorization Server with our own sso_application /
 * sso_oidc_saml_config rows, so /oauth2/authorize and /oauth2/token
 * recognize applications registered via
 * POST /api/v1/application-registration as valid OAuth2 clients — closing
 * the gap where the authorization server had no RegisteredClientRepository
 * bean at all.
 *
 * Deliberately READ-ONLY: application registration is the system of record
 * (ApplicationRegistrationService, itself @Audited), not Spring's dynamic
 * client-registration protocol. save() is unsupported on purpose.
 *
 * Relying parties are treated as PUBLIC clients (no stored client_secret —
 * there is no such column in sso_application, and the LLD's CRITICAL
 * CONSTRAINT rules out us inventing our own secret/crypto handling here).
 * PKCE (S256) is required instead, which is the standards-based way Spring
 * Security supports public clients — still "no custom crypto" territory,
 * just standard authorization_code + PKCE.
 *
 * Only applications with an OIDC config row are resolvable here — a
 * SAML-only application has no meaning as an OAuth2 client and correctly
 * returns empty (ADR-D3: SAML relying-party registration is deferred).
 */
@Component
public class SsoRegisteredClientRepository implements RegisteredClientRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SsoRegisteredClientRepository.class);

    private final SsoApplicationRepository applicationRepository;
    private final SsoOidcSamlConfigRepository configRepository;

    public SsoRegisteredClientRepository(SsoApplicationRepository applicationRepository,
                                          SsoOidcSamlConfigRepository configRepository) {
        this.applicationRepository = applicationRepository;
        this.configRepository = configRepository;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "Relying parties are registered via POST /api/v1/application-registration, "
                        + "not the Authorization Server's dynamic client registration.");
    }

    @Override
    public RegisteredClient findById(String id) {
        try {
            return applicationRepository.findById(UUID.fromString(id))
                    .flatMap(this::toRegisteredClient)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return applicationRepository.findByClientId(clientId)
                .flatMap(this::toRegisteredClient)
                .orElse(null);
    }

    private Optional<RegisteredClient> toRegisteredClient(SsoApplication application) {
        if (application.getStatus() != ApplicationStatus.ACTIVE) {
            LOGGER.info("Refusing to resolve OAuth2 client for non-ACTIVE application clientId={} status={}",
                    application.getClientId(), application.getStatus());
            return Optional.empty();
        }

        List<SsoOidcSamlConfig> configs = configRepository.findAllByApplicationId(application.getId());
        Optional<SsoOidcSamlConfig> oidcConfig = configs.stream()
                .filter(c -> c.getProtocol() == Protocol.OIDC)
                .findFirst();

        if (oidcConfig.isEmpty()) {
            LOGGER.info("No OIDC config for applicationId={}; not resolvable as an OAuth2 client "
                    + "(SAML-only relying parties don't go through /oauth2/authorize)", application.getId());
            return Optional.empty();
        }

        SsoOidcSamlConfig config = oidcConfig.get();
        String[] redirectUris = config.getRedirectUris().split(",");

        RegisteredClient.Builder builder = RegisteredClient.withId(application.getId().toString())
                .clientId(application.getClientId())
                .clientIdIssuedAt(application.getRegisteredAt())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("openid")
                .scope("profile")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true) // PKCE mandatory: this is how we stay standards-based
                        .requireAuthorizationConsent(false) // consent is handled by sso_consent_grant + SEC upstream
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build());

        for (String uri : redirectUris) {
            String trimmed = uri.trim();
            if (!trimmed.isEmpty()) {
                builder.redirectUri(trimmed);
            }
        }

        return Optional.of(builder.build());
    }
}
