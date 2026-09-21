package com.javarush.fedorov.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Partial update: fields that are not sent stay unchanged",
        example = "{\"name\": \"Alex Smith\"}")
public class UpdateUserRequestDto {

    @Schema(example = "Alex Smith")
    @Size(min = 1, max = 255, message = "Name mustn't be blank and mustn't exceed 255 characters")
    private String name;

    @Schema(example = "newPassword1")
    @Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
    private String password;
}
