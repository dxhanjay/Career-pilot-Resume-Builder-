package com.careerpilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata.
 *
 * <p>The endpoint documentation itself is generated from the same annotations
 * that drive validation and serialisation — {@code @Valid}, {@code @NotBlank},
 * the DTO records, the {@code @RestController} mappings. That is the whole
 * argument for generated documentation: hand-written API docs drift from the
 * implementation within a sprint and are then worse than no documentation,
 * because they are confidently wrong. Documentation derived from the code
 * cannot drift.
 *
 * <p>This class supplies only what cannot be inferred: title, description,
 * version, contact.
 *
 * <p><strong>Swagger is disabled in production.</strong> See
 * {@code application-prod.yml} — {@code springdoc.api-docs.enabled: false}. A
 * live OpenAPI document is a complete, machine-readable map of every endpoint,
 * parameter, and error condition in the system, which is a considerable
 * convenience for anyone probing it. The Postman collection covers the same need
 * for people who should have it.
 *
 * <p>The JWT security scheme is added in Phase 3, alongside the authentication
 * it documents.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Configuration
public class OpenApiConfig {

    private final String applicationVersion;

    public OpenApiConfig(@Value("${spring.application.version:0.1.0}") String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    /**
     * @return the OpenAPI document metadata
     */
    @Bean
    public OpenAPI careerPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareerPilot AI API")
                        .version(applicationVersion)
                        .description("""
                                AI-powered resume analysis and mock interview platform.

                                **Response envelope.** Every endpoint returns the same shape:
                                `success`, `data`, `message`, `timestamp`. Failures replace `data`
                                with a structured `error` carrying a stable `code` and a `traceId`.
                                Branch on `code`, never on the message text.

                                **Asynchronous operations.** Resume parsing, ATS analysis, job
                                matching, and interview evaluation exceed a reasonable HTTP
                                timeout. Those endpoints return `202 Accepted` with a job
                                identifier; poll `GET /api/v1/jobs/{id}` until the status is
                                terminal, then fetch the result named by `resultRef`.

                                **Not found vs forbidden.** A resource owned by another user
                                returns `404`, not `403`. This is deliberate: distinguishing the
                                two would confirm the existence of other users' data to anyone
                                enumerating identifiers.
                                """)
                        .contact(new Contact()
                                .name("CareerPilot AI")
                                .email("ai.altatech@gmail.com"))
                        .license(new License().name("Proprietary")));
    }
}
