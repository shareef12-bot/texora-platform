package com.texora.secops.vpn.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP-level chain is intentionally permit-all and stateless: authentication and authorization are done by
 * {@link com.texora.secops.vpn.security.RbacInterceptor}, which DENIES every /api/v1 handler that has no explicit
 * access rule. This avoids a second, competing credential check (no service implements its own - B.6.1) while
 * guaranteeing Spring Security's default "form login" chain is never silently applied.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain vpnSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
