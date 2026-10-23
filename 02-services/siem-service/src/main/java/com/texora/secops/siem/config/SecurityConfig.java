package com.texora.secops.siem.config;

import com.texora.secops.iam.token.TokenValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Administrative and analyst endpoints require a valid SSO-issued OAuth2/OIDC bearer token
 * (B.6.1). Per the CONFIRMED pattern in KNOWN-INTEGRATION-PITFALLS.md item 1, actual validation
 * happens in {@link com.texora.secops.siem.security.RbacInterceptor} via {@code texora-iam-client}'s
 * {@link TokenValidator}, called directly on the raw {@code Authorization} header - NOT through
 * Spring Security's own {@code .oauth2ResourceServer(...)} filter/SecurityContextHolder flow,
 * which this service's earlier draft used before that library's real shape was confirmed. This
 * class's Spring Security filter chain is therefore intentionally minimal: it disables CSRF and
 * session creation (a stateless REST API) and otherwise gets out of the way, since
 * authentication and authorization are enforced entirely by {@code RbacInterceptor}
 * (registered in {@link WebMvcConfig}) ahead of every annotated handler.
 *
 * <p>A {@link JwtDecoder} bean is still required here because {@code TokenValidator}'s
 * constructor takes one (see the confirmed signature in the pitfalls doc) - this is the one
 * piece of the original Spring Security resource-server wiring that is still needed, just no
 * longer used to drive an automatic authentication filter.
 *
 * <p>Ingestion endpoints are the one carve-out that stays permitted at this layer: sources are
 * platform services authenticating via mTLS, not end-user/admin callers (B.6.1, LLD §9.1), so
 * the raw ingestion path is exempted here and instead authenticated inside
 * {@code MultiProtocolIngestionAdapter} against the registered {@code siem_ingestion_source}
 * using the client certificate the mTLS terminator (ingress/service mesh) forwards.
 * {@code /actuator/health} is exposed to the internal network only at the network layer, per
 * LLD §6.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        // Deliberately no .oauth2ResourceServer(...) here - see class Javadoc. RbacInterceptor +
        // TokenValidator are the real authentication/authorization boundary for this service.
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(org.springframework.core.env.Environment env) {
        String jwkSetUri = env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public TokenValidator tokenValidator(JwtDecoder jwtDecoder) {
        return new TokenValidator(jwtDecoder);
    }
}
