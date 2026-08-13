package com.flowcrm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI flowCrmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowCRM API")
                        .description(
                                "Mini CRM backend with accounts, contacts, lead pipeline management, follow-up tasks, reminders, "
                                        + "durable idempotency, Redis caching/rate limiting/distributed locking, "
                                        + "transactional outbox, and RabbitMQ processing.")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Paste the JWT from /api/v1/auth/register or /api/v1/auth/login "
                                                        + "(Authorize → bearerAuth).")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .tags(List.of(
                        new Tag().name("Authentication").description("Register, login, and current user"),
                        new Tag().name("Accounts").description("Company/account CRM records"),
                        new Tag().name("Contacts").description("People records, optionally linked to an account"),
                        new Tag().name("Users").description("Directory of users for assignment (ADMIN)"),
                        new Tag().name("Leads").description("Lead CRUD and pipeline status transitions"),
                        new Tag().name("Tasks").description("Follow-up tasks and reminders"),
                        new Tag().name("Dashboard").description("Pipeline and task summary metrics"),
                        new Tag().name("Health").description("Service health check")));
    }
}
