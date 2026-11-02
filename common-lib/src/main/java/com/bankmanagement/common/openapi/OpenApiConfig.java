package com.bankmanagement.common.openapi;

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

/** OpenAPI metadata with a bearer-JWT scheme so Swagger UI can call secured endpoints. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI bankManagementOpenApi(@Value("${spring.application.name}") String appName,
                                    @Value("${app.openapi.server-url:/}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank Management System - " + appName)
                        .version("v1")
                        .description("Bank Management System " + appName + " API. Authenticate via /api/v1/auth/login, "
                                + "then use 'Authorize' with the access token.")
                        .contact(new Contact().name("Bank Management Platform Team")))
                .addServersItem(new Server().url(serverUrl))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
