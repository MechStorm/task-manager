package com.javarush.fedorov.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Schema(example = "{\"title\": \"Fix backend bugs\"}")
public class CreateTaskRequestDto {

    @Schema(description = "1–255 characters, not blank", example = "Fix backend bugs")
    @NotBlank(message = "Title can't be blank")
    @Size(max = 255, message = "Title mustn't exceed 255 characters")
    private String title;

    @Schema(description = "Owner of the task. Omit to put the task into the shared pool; "
            + "a `USER` may pass only their own id")
    private UUID userId;

    @Schema(description = "Optional; can't be in the past", example = "2026-12-31T18:00:00Z")
    @FutureOrPresent(message = "Deadline can't be in the past")
    private Instant deadline;
}
