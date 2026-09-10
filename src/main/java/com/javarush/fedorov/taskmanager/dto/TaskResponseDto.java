package com.javarush.fedorov.taskmanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResponseDto {
    private UUID id;
    private String title;
    private TaskStatus status;
    private Instant deadline;
    private UUID ownerId;
    private String ownerName;
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
