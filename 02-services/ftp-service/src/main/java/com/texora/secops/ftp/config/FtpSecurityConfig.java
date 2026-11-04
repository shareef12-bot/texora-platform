package com.texora.secops.ftp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stops Spring Boot's default "everything needs HTTP Basic" chain from engaging (the oauth2-resource-server starter
 * on the classpath would otherwise trigger it). Token validation and RBAC are NOT done here: they are enforced
 * fail-closed, per endpoint, by {@code RbacInterceptor} using the shared TokenValidator. Backs off if the shared
 * starter already supplies a SecurityFilterChain.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.security.config.annotation.web.builders.HttpSecurity")
public class FtpSecurityConfig {

    @Bean(name = "ftpSecurityFilterChain")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain ftpSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
