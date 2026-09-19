package com.javarush.fedorov.taskmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    @NotBlank(message = "Email mustn't be blank")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password mustn't be blank")
    private String password;
}
