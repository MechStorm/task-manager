package com.javarush.fedorov.taskmanager.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class CreateTaskRequestDto {

    @NotBlank(message = "Title can't be blank")
    @Size(max = 255, message = "Title mustn't exceed 255 characters")
    private String title;

    private UUID userId;

    @FutureOrPresent(message = "Deadline can't be in the past")
    private Instant deadline;
}
