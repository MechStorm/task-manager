package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("POST /api/auth/** — registration and login")
class AuthApiIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("registration creates a user, returns 201 with Location and never exposes the password")
    void registerCreatesUser() {
        RestTestClient.ResponseSpec response = client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "John", "email", "john@example.com", "password", DEFAULT_PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location).contains("/api/users/"));

        response.expectBody()
                .jsonPath("$.name").isEqualTo("John")
                .jsonPath("$.email").isEqualTo("john@example.com")
                .jsonPath("$.id").exists()
                .jsonPath("$.createdAt").exists()
                .jsonPath("$.password").doesNotExist()
                .jsonPath("$.role").doesNotExist();

        UUID id = UUID.fromString(bodyOf(response).read("$.id", String.class));

        User saved = userRepository.findById(id).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("john@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPassword())
                .as("the password must be stored hashed")
                .isNotEqualTo(DEFAULT_PASSWORD)
                .startsWith("$2");
    }

    @Test
    @DisplayName("self-registration cannot grant the ADMIN role")
    void registerIgnoresRoleFromPayload() {
        RestTestClient.ResponseSpec response = client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Bob\",\"email\":\"bob@example.com\",\"password\":\"password123\",\"role\":\"ADMIN\"}")
                .exchange()
                .expectStatus().isCreated();

        UUID id = UUID.fromString(bodyOf(response).read("$.id", String.class));

        assertThat(userRepository.findById(id).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("registering the same email twice — 409")
    void registerRejectsDuplicateEmail() {
        createUser("duplicate@example.com");

        client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "Duplicate", "email", "duplicate@example.com", "password", DEFAULT_PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Email already in use");
    }

    @Test
    @DisplayName("an invalid registration body — 400 with a per-field error map")
    void registerValidatesPayload() {
        client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "  ", "email", "not-an-email", "password", "short"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Request validation failed")
                .jsonPath("$.errors.name").exists()
                .jsonPath("$.errors.email").exists()
                .jsonPath("$.errors.password").exists();
    }

    @Test
    @DisplayName("broken JSON — 400 Malformed JSON request")
    void registerRejectsMalformedJson() {
        client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"John\", ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Malformed JSON request");
    }

    @Test
    @DisplayName("login returns a working JWT that opens a protected resource")
    void loginReturnsUsableToken() {
        createUser("login@example.com");

        RestTestClient.ResponseSpec response = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("email", "login@example.com", "password", DEFAULT_PASSWORD))
                .exchange()
                .expectStatus().isOk();

        String token = bodyOf(response).read("$.token", String.class);
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).as("a JWT consists of three parts").hasSize(3);

        client.get()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("login with a wrong password — 401")
    void loginRejectsWrongPassword() {
        createUser("wrongpass@example.com");

        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("email", "wrongpass@example.com", "password", "totally-wrong"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("login with an unknown email — 401 with the same message, no existence leak")
    void loginRejectsUnknownEmail() {
        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("email", "nobody@example.com", "password", DEFAULT_PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("an invalid login body — 400")
    void loginValidatesPayload() {
        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("email", "", "password", ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.email").exists()
                .jsonPath("$.errors.password").exists();
    }

    @Test
    @DisplayName("end-to-end path: registration → login → working with the API")
    void registerThenLoginThenUseApi() {
        client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "Newbie", "email", "newbie@example.com", "password", "newbiepass1"))
                .exchange()
                .expectStatus().isCreated();

        String token = login("newbie@example.com", "newbiepass1");

        client.post()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "First task"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.title").isEqualTo("First task")
                .jsonPath("$.status").isEqualTo("TO_DO");
    }
}
