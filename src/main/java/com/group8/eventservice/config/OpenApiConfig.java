package com.group8.eventservice.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    private static final String DESCRIPTION = """
            Group 8 event management: events, registrations, capacity and participation summaries.

            **Authentication.** Every endpoint needs `Authorization: Bearer <token>` with a Group 5 (Identity Service) \
            access token. Roles come from the token's `roles` claim: EVENT_ORGANIZER, ACADEMIC_STAFF, ADMIN, \
            ADMINISTRATIVE_STAFF, STUDENT.

            **Errors.** Every error has the shape `{ "success": false, "error": { "code", "message" } }`. Branch on \
            `error.code`; `error.message` is safe to show to users.

            | Code | HTTP | Meaning |
            |---|---|---|
            | VALIDATION_ERROR | 400 | Missing or invalid field, or bad filter / paging values |
            | INVALID_PARAMETER | 400 | Path or query value of the wrong type (e.g. id is not a UUID, unknown status) |
            | MALFORMED_REQUEST | 400 | Request body missing or not valid JSON |
            | INVALID_SCHEDULE | 400 | Dates do not fit together after an update |
            | INVALID_ELIGIBILITY_RULE | 400 | eligibilityRule is not in the supported format |
            | INVALID_STATE | 400 | Action not allowed in the current status |
            | EVENT_NOT_PUBLISHED / REGISTRATION_CLOSED | 400 | Registration not open |
            | CANCELLATION_CLOSED / EVENT_NOT_STARTED | 400 | Too late to cancel / too early to complete |
            | VENUE_NOT_FOUND | 400 | Group 6 does not know the venue code |
            | UNAUTHORIZED | 401 | Missing, invalid or expired token |
            | FORBIDDEN / NOT_VISIBLE | 403 | Role or ownership does not allow this |
            | NOT_ELIGIBLE / INVALID_USER | 403 | Group 5 rejected the registration |
            | NOT_FOUND | 404 | Event or registration does not exist |
            | METHOD_NOT_ALLOWED | 405 | Wrong HTTP method |
            | CAPACITY_REACHED / ALREADY_REGISTERED | 409 | Registration conflict |
            | VENUE_NOT_AVAILABLE | 409 | Group 6 says the venue cannot be used |
            | INTERNAL_ERROR | 500 | Unexpected error (no internal details) |
            | GROUP5_UNAVAILABLE / GROUP6_UNAVAILABLE | 503 | Dependency down; nothing was changed, retry later |

            **Tracing.** Every response has an `X-Request-ID` header. Send your own (e.g. from the API Gateway) to \
            follow one action across services.
            """;

    @Bean
    public OpenAPI eventServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event Service API")
                        .version("1.0")
                        .description(DESCRIPTION))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Every endpoint is served under /api/... and /api/v1/... (the API Gateway's form). Keep the
     * original operationIds on /api/... so generated clients don't change, and suffix the /api/v1
     * copies with "V1" (springdoc would otherwise number duplicates in no fixed order).
     */
    @Bean
    public OpenApiCustomizer stableOperationIds() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            String base = operation.getOperationId().replaceAll("_\\d+$", "");
            operation.setOperationId(path.startsWith("/api/v1/") ? base + "V1" : base);
        }));
    }

    /** Any endpoint can fail unexpectedly; document the (detail-free) 500 shape on all of them. */
    @Bean
    public OpenApiCustomizer internalErrorResponse() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().computeIfAbsent("500", code -> new ApiResponse()
                        .description("INTERNAL_ERROR: unexpected error, no internal details are returned")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))))));
    }
}
