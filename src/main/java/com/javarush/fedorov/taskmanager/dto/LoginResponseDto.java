package com.javarush.fedorov.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "JWT for the `Authorization: Bearer <token>` header; valid for 30 minutes by default",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private final String token;
}
