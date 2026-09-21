package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.dto.CreateUserRequestDto;
import com.javarush.fedorov.taskmanager.dto.UpdateUserRequestDto;
import com.javarush.fedorov.taskmanager.dto.UserResponseDto;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import com.javarush.fedorov.taskmanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User accounts")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get all users")
    @ApiResponse(responseCode = "200", description = "All users")
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @Operation(summary = "Get a user by id")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No user with this id")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @Operation(summary = "Create a user",
            description = "`ADMIN` only. Unlike self-registration, lets the admin choose the role (`USER` by default).")
    @ApiResponse(responseCode = "201", description = "User created",
            headers = @Header(name = "Location", description = "URI of the created user",
                    schema = @Schema(type = "string", example = "/api/users/3f2a1c4e-0000-0000-0000-000000000000")))
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "403", description = "The caller is not an `ADMIN`")
    @ApiResponse(responseCode = "409", description = "The email is already in use")
    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody CreateUserRequestDto requestDto) {
        UserResponseDto responseDto = userService.createUser(requestDto);
        URI location = URI.create("/api/users/" + responseDto.getId());
        return ResponseEntity.created(location).body(responseDto);
    }

    @Operation(summary = "Partially update a user",
            description = "Allowed to the user themselves or an `ADMIN`. Fields that are not sent stay unchanged; "
                    + "the email and the role can't be changed.")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "403", description = "Someone else's profile without `ADMIN`")
    @ApiResponse(responseCode = "404", description = "No user with this id")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequestDto user,
            @AuthenticationPrincipal UserSecureWrapper userSecureWrapper
    ) {
        UserResponseDto responseDto = userService.updateUser(id, user, userSecureWrapper.toCurrentUser());
        return ResponseEntity.ok(responseDto);
    }

    @Operation(summary = "Delete a user",
            description = "`ADMIN` only. The user's active tasks (`TO_DO`, `IN_PROGRESS`) go back to the shared pool "
                    + "as `TO_DO`; their `DONE` tasks stay `DONE` without an owner.")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @ApiResponse(responseCode = "403", description = "The caller is not an `ADMIN`")
    @ApiResponse(responseCode = "404", description = "No user with this id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
