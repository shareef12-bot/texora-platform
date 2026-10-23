package com.texora.secops.ldap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * <p>All authentication and authorisation is handled at the application layer:
 * <ul>
 *   <li>Token validation: {@link com.texora.secops.ldap.security.RbacInterceptor} calls
 *       {@code TokenValidator} from {@code texora-iam-client}.</li>
 *   <li>Role enforcement: {@link com.texora.secops.ldap.security.EndpointRoleRegistry}.</li>
 *   <li>Policy enforcement: {@link com.texora.secops.ldap.security.PolicyDecisionClient}.</li>
 * </ul>
 *
 * <p>Spring Security's filter chain is intentionally kept minimal — it provides
 * CSRF protection defaults but all endpoint access decisions are delegated to the
 * interceptor layer.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless API — no session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CSRF not applicable for stateless token-based API
            .csrf(AbstractHttpConfigurer::disable)
            // All access decisions are in RbacInterceptor; permit all here
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // No form login or HTTP Basic — token only
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
