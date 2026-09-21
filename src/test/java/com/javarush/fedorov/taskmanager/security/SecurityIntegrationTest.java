package com.javarush.fedorov.taskmanager.security;

import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@DisplayName("End-to-end security: JWT filter, entry points and actuator")
class SecurityIntegrationTest extends IntegrationTestBase {

    private Actor admin;
    private Actor alice;

    @BeforeEach
    void createActors() {
        admin = createAdmin("admin@security.test");
        alice = createUser("alice@security.test");
    }

    @Nested
    @DisplayName("token handling")
    class TokenHandling {

        @Test
        @DisplayName("a request without the Authorization header — 401 with a clear hint")
        void rejectsMissingHeader() {
            client.get()
                    .uri("/api/tasks")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Authentication required: provide a valid Bearer token");
        }

        @Test
        @DisplayName("a header without the Bearer scheme is ignored — 401")
        void rejectsHeaderWithoutBearerPrefix() {
            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.token())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("the Bearer scheme is case-sensitive — 401")
        void rejectsLowercaseBearerScheme() {
            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, "bearer " + alice.token())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("garbage instead of a token — 401")
        void rejectsGarbageToken() {
            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt-at-all")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("a forged signature — 401")
        void rejectsTokenSignedWithForeignKey() {
            SecretKey foreignKey = Keys.hmacShaKeyFor(
                    "another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8));

            String forged = Jwts.builder()
                    .subject(alice.email())
                    .issuer(issuer())
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
                    .signWith(foreignKey)
                    .compact();

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("an expired token — 401")
        void rejectsExpiredToken() {
            String expired = Jwts.builder()
                    .subject(alice.email())
                    .issuer(issuer())
                    .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                    .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                    .signWith(signingKey())
                    .compact();

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("a token from a foreign issuer — 401")
        void rejectsTokenFromForeignIssuer() {
            String foreignIssuer = Jwts.builder()
                    .subject(alice.email())
                    .issuer("evil-issuer")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
                    .signWith(signingKey())
                    .compact();

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignIssuer)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("a token without expiry is not accepted — 401")
        void rejectsTokenWithoutExpiration() {
            String endless = Jwts.builder()
                    .subject(alice.email())
                    .issuer(issuer())
                    .issuedAt(Date.from(Instant.now()))
                    .signWith(signingKey())
                    .compact();

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + endless)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("a technically valid token of a non-existent user — 401")
        void rejectsTokenOfUnknownUser() {
            String ghost = Jwts.builder()
                    .subject("ghost@security.test")
                    .issuer(issuer())
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
                    .signWith(signingKey())
                    .compact();

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ghost)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("the API issues no session cookie: authentication is fully stateless")
        void doesNotCreateSession() {
            client.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("email", alice.email(), "password", alice.password()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
        }

        private String issuer() {
            return securityProperties.getJwt().getIssuer();
        }

        private SecretKey signingKey() {
            return Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("actuator")
    class Actuator {

        @Test
        @DisplayName("health is open without authentication")
        void healthIsPublic() {
            client.get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("info is open without authentication")
        void infoIsPublic() {
            client.get()
                    .uri("/actuator/info")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Prometheus metrics are served without authentication")
        void prometheusExposesMetrics() {
            client.get()
                    .uri("/actuator/prometheus")
                    .accept(MediaType.TEXT_PLAIN)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .consumeWith(result -> assertThat(new String(
                            result.getResponseBody() == null ? new byte[0] : result.getResponseBody(),
                            StandardCharsets.UTF_8))
                            .contains("jvm_memory_used_bytes"));
        }

        @Test
        @DisplayName("the remaining actuator endpoints are closed: 401 anonymously, 403 for USER")
        void otherEndpointsRequireAdminRole() {
            client.get()
                    .uri("/actuator/metrics")
                    .exchange()
                    .expectStatus().isUnauthorized();

            client.get()
                    .uri("/actuator/metrics")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("admin passes actuator authorization (endpoint not exposed — 404, not 403)")
        void adminPassesAuthorizationOnActuator() {
            client.get()
                    .uri("/actuator/metrics")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("API documentation")
    class ApiDocumentation {

        @Test
        @DisplayName("the OpenAPI spec and the Swagger UI config are served without authentication")
        void openApiSpecIsPublic() {
            client.get()
                    .uri("/v3/api-docs")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.paths['/api/tasks']").exists();

            client.get()
                    .uri("/v3/api-docs/swagger-config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.url").isEqualTo("/v3/api-docs");
        }

        @Test
        @DisplayName("the Swagger UI page is served without authentication")
        void swaggerUiIsPublic() {
            client.get()
                    .uri("/swagger-ui/index.html")
                    .exchange()
                    .expectStatus().isOk();

            client.get()
                    .uri("/swagger-ui.html")
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .expectHeader().value(HttpHeaders.LOCATION,
                            location -> assertThat(location).endsWith("/swagger-ui/index.html"));
        }
    }
}
