package com.javarush.fedorov.taskmanager.service;

import com.javarush.fedorov.taskmanager.configuration.SecurityProperties;
import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("JwtService — issuing and validating tokens")
class JwtServiceTest {

    private static final String ISSUER = "task-manager-test";
    private static final String SECRET = "test-secret-test-secret-test-secret-test";

    private final JwtService jwtService = jwtService(ISSUER, SECRET, 30);

    @Test
    @DisplayName("an issued token is valid and carries the email in its subject")
    void generatesValidToken() {
        String token = jwtService.generateToken(principal("user@example.com"));

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractEmailFromToken(token)).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("too short a secret prevents the service from being created")
    void rejectsShortSecret() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> jwtService(ISSUER, "short-secret", 30))
                .withMessageContaining("at least 32");
    }

    @Test
    @DisplayName("an expired token is invalid and parsing its claims throws")
    void rejectsExpiredToken() {
        JwtService expiringService = jwtService(ISSUER, SECRET, -1);
        String token = expiringService.generateToken(principal("user@example.com"));

        assertThat(expiringService.validateToken(token)).isFalse();
        assertThatExceptionOfType(JwtException.class)
                .isThrownBy(() -> expiringService.extractEmailFromToken(token));
    }

    @Test
    @DisplayName("a token from another issuer is invalid")
    void rejectsForeignIssuer() {
        JwtService foreignIssuerService = jwtService("another-issuer", SECRET, 30);
        String token = foreignIssuerService.generateToken(principal("user@example.com"));

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("a token signed with a different key is invalid")
    void rejectsForeignSignature() {
        JwtService foreignKeyService = jwtService(ISSUER, "another-secret-another-secret-another-key", 30);
        String token = foreignKeyService.generateToken(principal("user@example.com"));

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("a token without expiry is invalid")
    void rejectsTokenWithoutExpiration() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String endless = Jwts.builder()
                .subject("user@example.com")
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .signWith(key)
                .compact();

        assertThat(jwtService.validateToken(endless)).isFalse();
        assertThatExceptionOfType(JwtException.class)
                .isThrownBy(() -> jwtService.extractEmailFromToken(endless))
                .withMessage("Token has no expiration");
    }

    @Test
    @DisplayName("a token with alg=none (unsigned) is invalid")
    void rejectsUnsignedToken() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        long expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES).getEpochSecond();

        String header = encoder.encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                ("{\"sub\":\"user@example.com\",\"iss\":\"" + ISSUER + "\",\"exp\":" + expiresAt + "}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(jwtService.validateToken(header + "." + payload + ".")).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is not a token")
    @ValueSource(strings = {"", " ", "garbage", "a.b.c", "Bearer"})
    @DisplayName("garbage instead of a token fails validation without breaking the service")
    void rejectsGarbage(String candidate) {
        assertThat(jwtService.validateToken(candidate)).isFalse();
    }

    private static JwtService jwtService(String issuer, String secret, long validityMinutes) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setIssuer(issuer);
        properties.getJwt().setSecret(secret);
        properties.getJwt().setAccessTokenValidityMinutes(validityMinutes);
        return new JwtService(properties);
    }

    private static UserSecureWrapper principal(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("User");
        user.setEmail(email);
        user.setPassword("hash");
        user.setRole(Role.USER);
        return new UserSecureWrapper(user);
    }
}
