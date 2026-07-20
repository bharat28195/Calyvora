package com.calyvora.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi setup. Declares the Bearer-JWT scheme so Swagger UI can call protected
 * endpoints, and stamps API metadata. Swagger UI is served at /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI calyvoraOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Calyvora Platform API")
                        .version("v1")
                        .description("Sprint 1 — tenancy, identity, authentication, RBAC, invitations."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
