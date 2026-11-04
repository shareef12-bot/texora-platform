package com.texora.secops.siem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "sso-bearer";

    @Bean
    public OpenAPI siemOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Texora SecOps - SOC/SIEM Log Server")
                        .version("v1")
                        .description("Platform-wide audit event ingestion, normalization, correlation and alerting."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("SSO-issued OAuth2/OIDC bearer token. Not required on /api/v1/ingest/** (mTLS-authenticated).")));
    }
}
