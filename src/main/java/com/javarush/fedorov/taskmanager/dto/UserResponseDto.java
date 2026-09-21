package com.javarush.fedorov.taskmanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.javarush.fedorov.taskmanager.model.entity.User;
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
@Schema(description = "The password, the role and the tasks are never returned")
public class UserResponseDto {

    @Schema(requiredMode = REQUIRED)
    private UUID id;

    @Schema(requiredMode = REQUIRED, example = "Alice")
    private String name;

    @Schema(requiredMode = REQUIRED, example = "alice@example.com")
    private String email;

    @Schema(requiredMode = REQUIRED)
    private Instant createdAt;

    private Instant updatedAt;

    public static UserResponseDto from(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
