package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.dto.CreateUserRequestDto;
import com.javarush.fedorov.taskmanager.dto.UpdateUserRequestDto;
import com.javarush.fedorov.taskmanager.dto.UserResponseDto;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import com.javarush.fedorov.taskmanager.service.UserService;
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
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody CreateUserRequestDto requestDto) {
        UserResponseDto responseDto = userService.createUser(requestDto);
        URI location = URI.create("/api/users/" + responseDto.getId());
        return ResponseEntity.created(location).body(responseDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequestDto user,
            @AuthenticationPrincipal UserSecureWrapper userSecureWrapper
    ) {
        UserResponseDto responseDto = userService.updateUser(id, user, userSecureWrapper.toCurrentUser());
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
