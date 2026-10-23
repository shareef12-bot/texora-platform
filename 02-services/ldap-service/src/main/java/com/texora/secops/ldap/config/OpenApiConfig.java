package com.texora.secops.ldap.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8087}")
    private int serverPort;

    @Bean
    public OpenAPI ldapServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Texora SecOps — LDAP Directory Service")
                        .version("1.0.0")
                        .description("""
                                LDAP Directory Service for the Texora SecOps platform.

                                **⚠ STUB MODE ACTIVE**: The LDAP engine adapter is a stub. \
                                All directory operations are no-ops until a real engine is wired (ADR-D5).

                                All endpoints require a Bearer token issued by the Texora IAM service.
                                Role requirements are documented per-endpoint.
                                """)
                        .contact(new Contact()
                                .name("Texora SecOps Platform Team")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by Texora IAM (texora-iam-client)")));
    }
}
