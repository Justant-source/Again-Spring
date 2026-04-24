package com.againspring.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.HashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 커스터마이저
 * Phase 14: API 문서 강화
 * - 표준 에러 응답 스키마 등록
 * - JWT Bearer 보안 스킴 추가
 * - 공통 응답 예시 추가
 */
@Configuration
public class OpenApiExamples {

    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            // Add ErrorResponse schema
            Schema<?> errorResponseSchema = new Schema<>()
                .type("object")
                .addProperty("error", new Schema<>()
                    .type("object")
                    .addProperty("code", new Schema<>().type("string").example("SESSION_NOT_FOUND"))
                    .addProperty("message", new Schema<>().type("string").example("세션을 찾을 수 없어요"))
                    .addProperty("timestamp", new Schema<>().type("string").format("date-time").example("2026-04-24T10:30:00Z"))
                    .addProperty("requestId", new Schema<>().type("string").example("req_abc123")));

            if (openApi.getComponents() == null) {
                openApi.components(new io.swagger.v3.oas.models.Components());
            }
            openApi.getComponents().addSchemas("ErrorResponse", errorResponseSchema);

            // Add JWT Bearer security scheme
            openApi.getComponents().addSecuritySchemes("bearer",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Bearer JWT token for authentication"));

            // Enhance components with common error responses
            addCommonErrorResponses(openApi);
        };
    }

    private void addCommonErrorResponses(OpenAPI openApi) {
        // 400 Bad Request
        ApiResponse badRequest = new ApiResponse()
            .description("Invalid input")
            .content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));

        // 401 Unauthorized
        ApiResponse unauthorized = new ApiResponse()
            .description("Authentication failed")
            .content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));

        // 403 Forbidden
        ApiResponse forbidden = new ApiResponse()
            .description("Access forbidden")
            .content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));

        // 404 Not Found
        ApiResponse notFound = new ApiResponse()
            .description("Resource not found")
            .content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));

        // 422 Unprocessable Entity
        ApiResponse crisisDetected = new ApiResponse()
            .description("Crisis detected")
            .content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>()
                    .type("object")
                    .addProperty("error", new Schema<>()
                        .type("object")
                        .addProperty("code", new Schema<>().type("string").example("CRISIS_DETECTED"))
                        .addProperty("message", new Schema<>().type("string").example("중요한 안내가 필요한 상황이 감지되었어요"))
                        .addProperty("crisisType", new Schema<>().type("string").example("domestic_violence"))
                        .addProperty("resources", new Schema<>().type("array"))))));

        // 500 Internal Server Error
        ApiResponse internalError = new ApiResponse()
            .description("Internal server error")
            .content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));

        // Store in components for reference (optional)
        Map<String, ApiResponse> commonResponses = new HashMap<>();
        commonResponses.put("400", badRequest);
        commonResponses.put("401", unauthorized);
        commonResponses.put("403", forbidden);
        commonResponses.put("404", notFound);
        commonResponses.put("422", crisisDetected);
        commonResponses.put("500", internalError);
    }

}
