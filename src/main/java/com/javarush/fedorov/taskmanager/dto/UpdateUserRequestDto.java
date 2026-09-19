package com.javarush.fedorov.taskmanager.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequestDto {

    @Size(min = 1, max = 255, message = "Name mustn't be blank and mustn't exceed 255 characters")
    private String name;

    @Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
    private String password;
}
