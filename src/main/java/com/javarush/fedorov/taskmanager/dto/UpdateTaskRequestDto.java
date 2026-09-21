package com.javarush.fedorov.taskmanager.dto;

import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Partial update: fields that are not sent stay unchanged",
        example = "{\"title\": \"Fix critical errors\"}")
public class UpdateTaskRequestDto {

    @Schema(description = "1–255 characters. Owner or `ADMIN` only", example = "Fix critical errors")
    @Size(min = 1, max = 255, message = "Title mustn't be blank and mustn't exceed 255 characters")
    private String title;

    @Schema(description = "New owner. A `USER` may pass only their own id (take the task)")
    private UUID userId;

    @Schema(description = "Can't be in the past. Owner or `ADMIN` only", example = "2026-12-31T18:00:00Z")
    @FutureOrPresent(message = "Deadline can't be in the past")
    private Instant deadline;

    @Schema(description = "Allowed transitions: `TO_DO` → `IN_PROGRESS`, `IN_PROGRESS` → `TO_DO` / `DONE`, "
            + "`DONE` → `IN_PROGRESS`. `IN_PROGRESS` and `DONE` require the task to have an owner")
    private TaskStatus status;
}
