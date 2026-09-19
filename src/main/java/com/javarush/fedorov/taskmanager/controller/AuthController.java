package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.dto.LoginRequestDto;
import com.javarush.fedorov.taskmanager.dto.LoginResponseDto;
import com.javarush.fedorov.taskmanager.dto.RegisterRequestDto;
import com.javarush.fedorov.taskmanager.dto.UserResponseDto;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import com.javarush.fedorov.taskmanager.service.JwtService;
import com.javarush.fedorov.taskmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto requestDto) {
        UserResponseDto userResponseDto = userService.register(requestDto);
        URI location = URI.create("/api/users/" + userResponseDto.getId());
        return ResponseEntity.created(location).body(userResponseDto);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requestDto.getEmail(), requestDto.getPassword())
        );

        UserSecureWrapper userSecureWrapper = (UserSecureWrapper) authentication.getPrincipal();
        String token = jwtService.generateToken(userSecureWrapper);

        return ResponseEntity.ok(new LoginResponseDto(token));
    }
}
