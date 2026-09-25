package com.texora.secops.sec.config;

import com.texora.secops.sec.security.RbacInterceptor;
import com.texora.secops.sec.security.ServiceAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RbacInterceptor rbacInterceptor;
    private final ServiceAuthInterceptor serviceAuthInterceptor;

    public WebMvcConfig(RbacInterceptor rbacInterceptor,
                        ServiceAuthInterceptor serviceAuthInterceptor) {
        this.rbacInterceptor = rbacInterceptor;
        this.serviceAuthInterceptor = serviceAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ServiceAuthInterceptor: mTLS endpoints (policy evaluation + secret resolution)
        registry.addInterceptor(serviceAuthInterceptor)
                .addPathPatterns(
                        "/api/v1/policies/evaluate",
                        "/api/v1/secret-references/*/resolve");

        // RbacInterceptor: all SSO-authenticated admin/lifecycle endpoints
        registry.addInterceptor(rbacInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/policies/evaluate",
                        "/api/v1/secret-references/*/resolve",
                        "/actuator/**");
    }
}
