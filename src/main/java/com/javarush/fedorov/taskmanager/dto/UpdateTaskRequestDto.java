package com.javarush.fedorov.taskmanager.dto;

import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class UpdateTaskRequestDto {

    @Size(min = 1, max = 255, message = "Title mustn't be blank and mustn't exceed 255 characters")
    private String title;

    private UUID userId;

    @FutureOrPresent(message = "Deadline can't be in the past")
    private Instant deadline;

    private TaskStatus status;
}
