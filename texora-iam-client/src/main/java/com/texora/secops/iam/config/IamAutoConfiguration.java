package com.texora.secops.iam.config;

import com.texora.secops.iam.interceptor.RbacInterceptor;
import com.texora.secops.iam.mtls.MtlsClientHelper;
import com.texora.secops.iam.mtls.MtlsProperties;
import com.texora.secops.iam.token.TokenValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for the IAM client library.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * Services include this library on the classpath and it wires itself.
 *
 * <p>Requires the following properties:
 * <pre>
 *   texora.iam.jwks-uri=https://sso.texora.internal/oauth2/jwks
 *   texora.iam.issuer-uri=https://sso.texora.internal
 * </pre>
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties({IamProperties.class, MtlsProperties.class})
public class IamAutoConfiguration implements WebMvcConfigurer {

    private final IamProperties iamProperties;
    private final MtlsProperties mtlsProperties;

    public IamAutoConfiguration(IamProperties iamProperties, MtlsProperties mtlsProperties) {
        this.iamProperties  = iamProperties;
        this.mtlsProperties = mtlsProperties;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
            .withJwkSetUri(iamProperties.getJwksUri())
            .build();
    }

    @Bean
    public TokenValidator tokenValidator(JwtDecoder jwtDecoder) {
        return new TokenValidator(jwtDecoder);
    }

    @Bean
    public RbacInterceptor rbacInterceptor(TokenValidator tokenValidator) {
        return new RbacInterceptor(tokenValidator);
    }

    @Bean
    @ConditionalOnProperty(name = "texora.iam.mtls.enabled", havingValue = "true")
    public MtlsClientHelper mtlsClientHelper() {
        return new MtlsClientHelper(mtlsProperties);
    }

    /**
     * Registers the {@link RbacInterceptor} for all paths under {@code /api/}.
     * Actuator endpoints ({@code /actuator/**}) are excluded — they are
     * secured at the network level via Kubernetes NetworkPolicy.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rbacInterceptor(tokenValidator(jwtDecoder())))
            .addPathPatterns("/api/**")
            .excludePathPatterns("/actuator/**");
    }
}
