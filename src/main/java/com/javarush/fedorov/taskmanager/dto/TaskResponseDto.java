package com.javarush.fedorov.taskmanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Fields without a value are omitted from the response")
public class TaskResponseDto {
    @Schema(requiredMode = REQUIRED)
    private UUID id;

    @Schema(requiredMode = REQUIRED, example = "Buy groceries")
    private String title;

    @Schema(requiredMode = REQUIRED)
    private TaskStatus status;

    @Schema(example = "2026-12-31T18:00:00Z")
    private Instant deadline;

    @Schema(description = "Absent when the task is in the shared pool")
    private UUID ownerId;

    @Schema(description = "Absent when the task is in the shared pool", example = "Alice")
    private String ownerName;

    @Schema(requiredMode = REQUIRED)
    private Instant createdAt;

    private Instant updatedAt;

    public static TaskResponseDto from(Task task) {
        User owner = task.getOwner();
        return TaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .status(task.getStatus())
                .deadline(task.getDeadline())
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getName() : null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
