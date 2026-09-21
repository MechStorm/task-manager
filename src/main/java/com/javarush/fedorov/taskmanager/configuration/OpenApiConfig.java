package com.javarush.fedorov.taskmanager.configuration;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TreeMap;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Task Manager API", version = "v1",
                description = "REST API for task and users management"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    private static final String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";

    @Bean
    public OpenApiCustomizer problemDetailResponsesCustomizer() {
        return openApi -> {
            ModelConverters.getInstance(openApi.getSpecVersion() == SpecVersion.V31)
                    .resolveAsResolvedSchema(new AnnotatedType(ProblemDetailSchema.class))
                    .referencedSchemas
                    .forEach(openApi.getComponents()::addSchemas);

            Content problemContent = new Content().addMediaType(APPLICATION_PROBLEM_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref(PROBLEM_DETAIL_REF)));

            openApi.getPaths().values().stream()
                    .flatMap(pathItem -> pathItem.readOperations().stream())
                    .forEach(operation -> {
                        ApiResponses responses = operation.getResponses();

                        if (requiresAuthentication(operation)) {
                            responses.putIfAbsent("401",
                                    new ApiResponse().description("Missing, invalid or expired Bearer token"));
                        }

                        responses.forEach((code, response) -> {
                            if (code.startsWith("4") || code.startsWith("5")) {
                                response.setContent(problemContent);
                            }
                        });

                        ApiResponses sorted = new ApiResponses();
                        new TreeMap<>(responses).forEach(sorted::addApiResponse);
                        operation.setResponses(sorted);
                    });
        };
    }

    private static boolean requiresAuthentication(Operation operation) {
        return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
    }
}
