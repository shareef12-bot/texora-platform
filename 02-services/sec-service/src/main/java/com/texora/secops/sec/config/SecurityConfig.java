package com.texora.secops.sec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security configuration for the SEC service.
 *
 * <p>Two auth paths (B.6):</p>
 * <ul>
 *   <li>SSO token (JWT) for human/admin callers — validated by the OAuth2 resource server</li>
 *   <li>mTLS for service-to-service calls — handled by ServiceAuthInterceptor</li>
 * </ul>
 *
 * <p>Fail closed: any ambiguity or missing config results in DENY (B.6 §3).</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Actuator health — internal network only; no auth required
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // mTLS service endpoints — authenticated by ServiceAuthInterceptor, not Spring Security
                // (mTLS cert validation happens at the TLS layer; interceptor authorizes by CN)
                .requestMatchers("/api/v1/policies/evaluate").permitAll()
                .requestMatchers("/api/v1/secret-references/*/resolve").permitAll()
                // Everything else requires a valid SSO JWT
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extract roles/permissions from the 'roles' claim in the SSO JWT
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof Collection<?> roles) {
                return roles.stream()
                        .filter(r -> r instanceof String)
                        .map(r -> new SimpleGrantedAuthority((String) r))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        });
        return converter;
    }
}
