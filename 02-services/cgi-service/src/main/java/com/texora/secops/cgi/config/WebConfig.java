package com.texora.secops.cgi.config;

import com.texora.secops.cgi.config.props.SecurityProperties;
import com.texora.secops.cgi.security.RbacInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/** Registers the RBAC interceptor on everything except health and (optionally, local only) the API docs. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RbacInterceptor rbacInterceptor;
    private final SecurityProperties securityProperties;

    public WebConfig(RbacInterceptor rbacInterceptor, SecurityProperties securityProperties) {
        this.rbacInterceptor = rbacInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> excluded = new ArrayList<>(List.of("/error", "/actuator/health", "/actuator/health/**"));
        if (securityProperties.isExposeApiDocs()) {
            excluded.addAll(List.of("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"));
        }
        registry.addInterceptor(rbacInterceptor).addPathPatterns("/**").excludePathPatterns(excluded);
    }
}
