package com.javarush.fedorov.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    @Schema(example = "alice@example.com")
    @NotBlank(message = "Email mustn't be blank")
    @Email(message = "Email must be a valid email address")
    private String email;

    @Schema(example = "password123")
    @NotBlank(message = "Password mustn't be blank")
    private String password;
}
