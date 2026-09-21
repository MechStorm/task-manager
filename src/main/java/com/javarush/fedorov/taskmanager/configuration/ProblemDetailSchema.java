package com.javarush.fedorov.taskmanager.configuration;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(name = "ProblemDetail", description = "Error body returned by every failed request")
public record ProblemDetailSchema(
        @Schema(example = "about:blank")
        String type,

        @Schema(example = "Not Found")
        String title,

        @Schema(example = "404")
        int status,

        @Schema(example = "Task with id 3f2a1c4e-0000-0000-0000-000000000000 not found")
        String detail,

        @Schema(example = "/api/tasks/3f2a1c4e-0000-0000-0000-000000000000")
        String instance,

        @Schema(description = "Field-level messages; present only when the request body fails validation",
                example = "{\"title\": \"Title can't be blank\"}")
        Map<String, String> errors
) {
}
