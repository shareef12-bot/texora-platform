package com.texora.secops.sso.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes the OpenAPI 3 spec (deliverable D-004) via springdoc at /v3/api-docs and /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ssoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Texora SecOps — SSO / IAM Service")
                .version("v1")
                .description("Identity Domain — single point of authentication trust for the platform."));
    }
}
