package com.texora.secops.ftp.config;

import com.texora.secops.ftp.security.RbacInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FtpWebConfig implements WebMvcConfigurer {

    private final RbacInterceptor rbacInterceptor;

    public FtpWebConfig(RbacInterceptor rbacInterceptor) {
        this.rbacInterceptor = rbacInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Every business endpoint. /actuator/** is internal-only and not routed through the gateway.
        registry.addInterceptor(rbacInterceptor).addPathPatterns("/api/v1/**");
    }
}
