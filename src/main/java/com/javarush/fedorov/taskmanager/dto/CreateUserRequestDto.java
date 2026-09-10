package com.javarush.fedorov.taskmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequestDto {

    @NotBlank(message = "Name mustn't be blank")
    @Size(max = 255, message = "Name mustn't exceed 255 characters")
    private String name;

    @NotBlank(message = "Email mustn't be blank")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email mustn't exceed 255 characters")
    private String email;

    @NotBlank(message = "Password mustn't be blank")
    @Size(min = 8, max = 25, message = "Password must be between 8 and 25 characters")
    private String password;
}
