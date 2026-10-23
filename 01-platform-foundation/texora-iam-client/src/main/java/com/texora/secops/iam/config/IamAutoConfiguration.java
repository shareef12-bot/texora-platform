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
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for the IAM client library.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>Requires:
 * <pre>
 *   texora.iam.jwks-uri=https://sso.texora.internal/oauth2/jwks
 *   texora.iam.issuer-uri=https://sso.texora.internal
 * </pre>
 *
 * <p>Services that register their own auth interceptor (e.g. SEC) can opt out of
 * the library's RbacInterceptor with {@code texora.iam.rbac.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties({IamProperties.class, MtlsProperties.class})
public class IamAutoConfiguration {

    private final IamProperties iamProperties;
    private final MtlsProperties mtlsProperties;

    public IamAutoConfiguration(IamProperties iamProperties, MtlsProperties mtlsProperties) {
        this.iamProperties = iamProperties;
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
    @ConditionalOnProperty(name = "texora.iam.rbac.enabled", havingValue = "true", matchIfMissing = true)
    public RbacInterceptor rbacInterceptor(TokenValidator tokenValidator) {
        return new RbacInterceptor(tokenValidator);
    }

    @Bean
    @ConditionalOnProperty(name = "texora.iam.mtls.enabled", havingValue = "true")
    public MtlsClientHelper mtlsClientHelper() {
        return new MtlsClientHelper(mtlsProperties);
    }

    /**
     * Registers the library {@link RbacInterceptor} for all paths under {@code /api/}.
     * Actuator endpoints are excluded (secured at network level via NetworkPolicy).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "texora.iam.rbac.enabled", havingValue = "true", matchIfMissing = true)
    static class IamWebMvcConfig implements WebMvcConfigurer {

        private final RbacInterceptor rbacInterceptor;

        IamWebMvcConfig(RbacInterceptor rbacInterceptor) {
            this.rbacInterceptor = rbacInterceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(rbacInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/actuator/**");
        }
    }
}