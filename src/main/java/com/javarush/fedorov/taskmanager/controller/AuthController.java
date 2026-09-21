package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.dto.LoginRequestDto;
import com.javarush.fedorov.taskmanager.dto.LoginResponseDto;
import com.javarush.fedorov.taskmanager.dto.RegisterRequestDto;
import com.javarush.fedorov.taskmanager.dto.UserResponseDto;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import com.javarush.fedorov.taskmanager.service.JwtService;
import com.javarush.fedorov.taskmanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirements
@Tag(name = "Authentication", description = "Public endpoints: registration and obtaining a JWT")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    @Operation(summary = "Register a new user", description = "Self-registration always creates a `USER`.")
    @ApiResponse(responseCode = "201", description = "User registered",
            headers = @Header(name = "Location", description = "URI of the created user",
                    schema = @Schema(type = "string", example = "/api/users/3f2a1c4e-0000-0000-0000-000000000000")))
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "409", description = "The email is already in use")
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto requestDto) {
        UserResponseDto userResponseDto = userService.register(requestDto);
        URI location = URI.create("/api/users/" + userResponseDto.getId());
        return ResponseEntity.created(location).body(userResponseDto);
    }

    @Operation(summary = "Log in and get a JWT",
            description = "Send the token as `Authorization: Bearer <token>`. In Swagger UI press **Authorize** "
                    + "and paste the token without the `Bearer` prefix.")
    @ApiResponse(responseCode = "200", description = "Credentials accepted")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "401", description = "Wrong email or password")
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
