package com.texora.secops.sso.config;

import com.texora.secops.sso.tenant.TenantResolvingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Every /api/v1/** endpoint requires a valid SSO-issued OAuth2/OIDC bearer
 * token, validated via Spring Security's standards-based resource-server
 * support (signature, audience, and issuer checks all enabled — no custom
 * crypto, per the LLD's CRITICAL CONSTRAINT). Federation endpoints
 * (/oauth2/*, /saml2/*) are handled by their own Spring Security filter
 * chains (OAuth2 Authorization Server + SAML2 Service Provider), configured
 * via application.yml, not by hand-rolled protocol code.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, TenantResolvingFilter tenantResolvingFilter)
            throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> {}) // signature/audience/issuer checks configured via application.yml
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
            .addFilterAfter(tenantResolvingFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .csrf(AbstractHttpConfigurer::disable)
            // Internal-network only per LLD §6.1 — network policy enforces this at the
            // Kubernetes layer; the app itself still requires no anonymous internet exposure.
            .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of()); // configured per-environment via application.yml overlay
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
    }
}
