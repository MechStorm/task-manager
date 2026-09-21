package com.javarush.fedorov.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDto {

    @Schema(description = "1–255 characters, not blank", example = "Alice")
    @NotBlank(message = "Name mustn't be blank")
    @Size(max = 255, message = "Name mustn't exceed 255 characters")
    private String name;

    @Schema(description = "Must be unique", example = "alice@example.com")
    @NotBlank(message = "Email mustn't be blank")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email mustn't exceed 255 characters")
    private String email;

    @Schema(example = "password123")
    @NotBlank(message = "Password mustn't be blank")
    @Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
    private String password;
}
