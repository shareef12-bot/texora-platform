package com.texora.secops.sso.config;

import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.repository.SsoApplicationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;

/**
 * Adds the platform claims every service's TokenValidator requires:
 *   ten   = the tenant of the application being logged into
 *   roles = empty until the DC profile is wired in (a placeholder, NOT a grant)
 * Fail-closed: an unknown application means no token is issued.
 */
@Configuration
public class TokenClaimsCustomizer {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> platformClaimsCustomizer(
            SsoApplicationRepository applicationRepository) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            String clientId = context.getRegisteredClient().getClientId();
            SsoApplication application = applicationRepository.findByClientId(clientId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No application for clientId=" + clientId + " - refusing to issue token"));
            context.getClaims().claim("ten", application.getTenantId().toString());
            context.getClaims().claim("roles", List.of());
        };
    }
}
