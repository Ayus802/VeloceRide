package com.rideshare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Swagger UI: /swagger-ui/index.html - OpenAPI JSON: /v3/api-docs.
 * Click "Authorize" and paste the accessToken from /api/auth/login (without the "Bearer " prefix).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    private static final Map<String, String> ERROR_RESPONSES = Map.of(
            "400", "Validation failed or malformed request",
            "401", "Missing, invalid or expired access token",
            "403", "Authenticated but not allowed (not the owner / not an admin)",
            "404", "Resource not found",
            "409", "Conflicts with the current state (duplicate, wrong status, ...)",
            "422", "Business rule violated (e.g. not enough seats)",
            "500", "Unexpected server error");

    @Bean
    public OpenAPI rideShareOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideShare API")
                        .version("1.0.0")
                        .description("""
                                Backend for a ride-sharing platform. **One account, one profile, one user id, two modes**:
                                every user can publish rides as a driver (RIDER mode) and book seats as a passenger \
                                (PASSENGER mode). The active mode is a UI preference only; authorization is based on \
                                ownership (driver of the ride, passenger of the booking) and the ADMIN role.

                                All responses use the envelope `{ success, message, data | errors, timestamp }`.
                                List endpoints accept `page`, `size` (max 100) and `sort` (e.g. `sort=createdAt,desc`).

                                Real-time features (live location, notifications) use STOMP over WebSocket at `/ws`; \
                                see `docs/WEBSOCKET_API.md`.""")
                        .contact(new Contact().name("RideShare Backend")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from /api/auth/login or /api/auth/register"))
                        .addSchemas("ErrorResponse", new Schema<>()
                                .type("object")
                                .addProperty("success", new Schema<>().type("boolean").example(false))
                                .addProperty("message", new Schema<>().type("string").example("Ride not found"))
                                .addProperty("errors", new Schema<>().type("array").items(new Schema<>()
                                        .type("object")
                                        .addProperty("field", new Schema<>().type("string"))
                                        .addProperty("message", new Schema<>().type("string"))))
                                .addProperty("timestamp", new Schema<>().type("string").example("2026-11-15T10:30:00"))))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .tags(List.of(
                        new Tag().name("1. Authentication"), new Tag().name("2. Users"), new Tag().name("3. Vehicles"),
                        new Tag().name("4. Rides"), new Tag().name("5. Bookings"), new Tag().name("6. Payments"),
                        new Tag().name("7. Reviews & Ratings"), new Tag().name("8. Notifications"),
                        new Tag().name("9. Live Location"), new Tag().name("10. Admin")));
    }

    /** Documents the standard error envelope for the usual error codes on every operation. */
    @Bean
    public OperationCustomizer standardErrorResponses() {
        return (Operation operation, org.springframework.web.method.HandlerMethod handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            ERROR_RESPONSES.forEach((code, description) -> {
                if (!responses.containsKey(code)) {
                    responses.addApiResponse(code, new ApiResponse()
                            .description(description)
                            .content(new Content().addMediaType("application/json", new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                                    .addExamples("example", new Example().value(Map.of(
                                            "success", false,
                                            "message", description,
                                            "errors", List.of(),
                                            "timestamp", "2026-11-15T10:30:00"))))));
                }
            });
            return operation;
        };
    }
}
