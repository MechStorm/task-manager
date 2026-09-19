package com.javarush.fedorov.taskmanager.service;

import com.javarush.fedorov.taskmanager.configuration.SecurityProperties;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final String issuer;
    private final Duration expirationTime;

    public JwtService(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.getJwt();

        byte[] keyBytes = jwt.getSecret().getBytes(StandardCharsets.UTF_8);

        if(keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("Secret must be at least " + MIN_SECRET_BYTES + " characters long");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = jwt.getIssuer();
        this.expirationTime = Duration.ofMinutes(jwt.getAccessTokenValidityMinutes());
    }

    public String generateToken(UserSecureWrapper userSecureWrapper) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userSecureWrapper.getUsername())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationTime)))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.getExpiration() == null) {
            throw new JwtException("Token has no expiration");
        }

        return claims;
    }
}
