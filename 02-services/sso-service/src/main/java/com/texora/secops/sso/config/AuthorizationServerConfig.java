package com.texora.secops.sso.config;

import com.nimbusds.jose.jwk.JWKSet;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.texora.secops.sso.tenant.TenantContextCleanupFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Wires Spring's standards-based OAuth2 Authorization Server for
 * /oauth2/authorize, /oauth2/token, /oauth2/jwks — no hand-rolled protocol
 * or signing code anywhere in this class, per the LLD's CRITICAL CONSTRAINT.
 *
 * Two filter chains:
 *   1. authorizationServerSecurityFilterChain — matches exactly the
 *      authorization-server endpoints. Unauthenticated requests are
 *      redirected to our own /login page (LoginController), which is what
 *      actually invokes LoginOrchestrationService — NOT Spring's default
 *      form-login username/password check.
 *   2. defaultSecurityFilterChain — everything else on the browser side
 *      (currently just /login itself). Wraps requests in
 *      TenantContextCleanupFilter since, unlike the /api/v1/** chain, there
 *      is no bearer token yet for TenantResolvingFilter to read tenant from.
 */
@Configuration
public class AuthorizationServerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    @Bean
    @Order(0)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
            .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
            .with(authorizationServerConfigurer, configurer -> configurer
                    .oidc(Customizer.withDefaults())) // enables the OIDC layer (userinfo, discovery) on top of OAuth2
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            .addFilterBefore(new TenantContextCleanupFilter(),
                    org.springframework.security.web.context.SecurityContextHolderFilter.class);

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/login/**", "/login")
            .csrf(csrf -> csrf.ignoringRequestMatchers("/login")) // see README: simplification, not a recommendation
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .addFilterBefore(new TenantContextCleanupFilter(),
                    org.springframework.security.web.context.SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${texora.sso.issuer-uri:${SSO_ISSUER_URI:https://sso.texora.internal}}") String issuer) {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }

    /**
     * Signing key source for issued tokens. In every real environment this
     * MUST be swapped for a key retrieved from SEC/KMS (the signing_cert_ref
     * pattern already used elsewhere in this schema), NOT an
     * in-process-generated key — this generates an ephemeral RSA keypair on
     * startup purely so /oauth2/authorize and /oauth2/token are actually
     * runnable end-to-end in this deliverable and in tests. Wiring a real
     * KMS-backed JWKSource is tracked as a follow-up (see README); nothing
     * about the signing ALGORITHM changes when that swap happens — this is
     * still 100% Spring Security's standard RSA/JWT handling either way.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        LOGGER.warn("Using an EPHEMERAL in-process signing key. This is only acceptable for local/dev/test "
                + "runs — production MUST source signing material from SEC/KMS via signing_cert_ref.");
        RSAKey rsaKey = generateRsaKey();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate signing key", e);
        }
    }
}
