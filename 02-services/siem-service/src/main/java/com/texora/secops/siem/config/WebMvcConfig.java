package com.texora.secops.siem.config;

import com.texora.secops.siem.security.RbacInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link RbacInterceptor} on every admin/analyst path. Ingestion endpoints
 * (/api/v1/ingest/**) and Actuator health are excluded because they are not SSO-token-authenticated
 * (see SecurityConfig) and enforce their own authentication instead.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RbacInterceptor rbacInterceptor;

    public WebMvcConfig(RbacInterceptor rbacInterceptor) {
        this.rbacInterceptor = rbacInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rbacInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/ingest/**", "/api/v1/docs/**", "/api/v1/openapi/**");
    }
}
