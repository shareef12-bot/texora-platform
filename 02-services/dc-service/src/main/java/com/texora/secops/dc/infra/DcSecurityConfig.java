package com.texora.secops.dc.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Replaces Spring Boot's default "every request needs a JWT" chain (the shared IAM client brings the
 * oauth2-resource-server starter). Authentication and RBAC for /api/v1/** are enforced fail-closed by
 * RbacInterceptor using the shared TokenValidator, so the filter chain must not validate tokens itself.
 * Only /api/v1/** and the health endpoint are open at this layer. Everything else (metrics, info,
 * swagger, api-docs) stays closed. Backs off if a shared library supplies its own SecurityFilterChain.
 */
@Configuration
public class DcSecurityConfig {

    @Bean(name = "dcSecurityFilterChain")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain dcSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}