package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("/api/users — user management")
class UserApiIntegrationTest extends IntegrationTestBase {

    private Actor admin;
    private Actor alice;
    private Actor bob;

    @BeforeEach
    void createActors() {
        admin = createAdmin("admin@users.test");
        alice = createUser("alice@users.test");
        bob = createUser("bob@users.test");
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("the user list is available to any authenticated user, without passwords")
        void listsUsersWithoutPasswords() {
            client.get()
                    .uri("/api/users")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(3)
                    .jsonPath("$[0].password").doesNotExist()
                    .jsonPath("$[0].role").doesNotExist()
                    .jsonPath("$[?(@.email == 'alice@users.test')].name").exists();
        }

        @Test
        @DisplayName("a user read by id")
        void findsUserById() {
            client.get()
                    .uri("/api/users/{id}", bob.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(bob.id().toString())
                    .jsonPath("$.email").isEqualTo(bob.email())
                    .jsonPath("$.password").doesNotExist();
        }

        @Test
        @DisplayName("a non-existent id — 404")
        void returnsNotFoundForUnknownId() {
            UUID missing = UUID.randomUUID();

            client.get()
                    .uri("/api/users/{id}", missing)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("User not found: " + missing);
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousReading() {
            client.get().uri("/api/users").exchange().expectStatus().isUnauthorized();
            client.get().uri("/api/users/{id}", alice.id()).exchange().expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("creation by an admin")
    class Creation {

        @Test
        @DisplayName("admin creates a user with the default USER role")
        void adminCreatesUserWithDefaultRole() {
            RestTestClient.ResponseSpec response = client.post()
                    .uri("/api/users")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "New user", "email", "created@users.test", "password", DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location).contains("/api/users/"));

            UUID id = UUID.fromString(bodyOf(response).read("$.id", String.class));
            User created = userRepository.findById(id).orElseThrow();

            assertThat(created.getRole()).isEqualTo(Role.USER);
            assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, created.getPassword())).isTrue();
        }

        @Test
        @DisplayName("admin creates another admin, who immediately has admin rights")
        void adminCreatesAnotherAdmin() {
            client.post()
                    .uri("/api/users")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Second admin", "email", "admin2@users.test",
                            "password", DEFAULT_PASSWORD, "role", "ADMIN"))
                    .exchange()
                    .expectStatus().isCreated();

            String secondAdminToken = login("admin2@users.test", DEFAULT_PASSWORD);
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.delete()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAdminToken)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("a regular user cannot create users — 403")
        void userCannotCreateUsers() {
            client.post()
                    .uri("/api/users")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Impostor", "email", "intruder@users.test",
                            "password", DEFAULT_PASSWORD, "role", "ADMIN"))
                    .exchange()
                    .expectStatus().isForbidden();

            assertThat(userRepository.findByEmail("intruder@users.test")).isEmpty();
        }

        @Test
        @DisplayName("an email that is already taken — 409")
        void rejectsDuplicateEmail() {
            client.post()
                    .uri("/api/users")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Duplicate", "email", alice.email(), "password", DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Email already in use");
        }

        @Test
        @DisplayName("an invalid body — 400")
        void validatesPayload() {
            client.post()
                    .uri("/api/users")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "", "email", "broken", "password", "1234567"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.name").exists()
                    .jsonPath("$.errors.email").exists()
                    .jsonPath("$.errors.password").exists();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousCreation() {
            client.post()
                    .uri("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Anonymous", "email", "anon@users.test", "password", DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("updating")
    class Updating {

        @Test
        @DisplayName("a user changes their own name")
        void userUpdatesOwnName() {
            client.put()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Alice Renamed"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("Alice Renamed")
                    .jsonPath("$.email").isEqualTo(alice.email());
        }

        @Test
        @DisplayName("end-to-end path: password change — the new one works, the old one does not")
        void userChangesOwnPassword() {
            client.put()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("password", "newpassword1"))
                    .exchange()
                    .expectStatus().isOk();

            String newToken = login(alice.email(), "newpassword1");
            assertThat(newToken).isNotBlank();

            client.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("email", alice.email(), "password", DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("a user cannot edit another user's profile — 403")
        void userCannotUpdateSomeoneElse() {
            client.put()
                    .uri("/api/users/{id}", bob.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Hacked"))
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("You can only update your own profile");

            assertThat(userRepository.findById(bob.id()).orElseThrow().getName()).isEqualTo(bob.name());
        }

        @Test
        @DisplayName("admin edits another user's profile")
        void adminUpdatesAnyProfile() {
            client.put()
                    .uri("/api/users/{id}", bob.id())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Bob renamed by the admin"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("Bob renamed by the admin");
        }

        @Test
        @DisplayName("the access check runs before the lookup: another user's non-existent id gives 403")
        void accessCheckHappensBeforeLookup() {
            client.put()
                    .uri("/api/users/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Does not matter"))
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("admin updates a non-existent user — 404")
        void adminGetsNotFoundForUnknownUser() {
            client.put()
                    .uri("/api/users/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Does not matter"))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("an empty body is accepted and changes nothing")
        void emptyPayloadIsNoOp() {
            client.put()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo(alice.name());

            assertThat(passwordEncoder.matches(DEFAULT_PASSWORD,
                    userRepository.findById(alice.id()).orElseThrow().getPassword())).isTrue();
        }

        @Test
        @DisplayName("too short a password and a blank name — 400")
        void validatesPayload() {
            client.put()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "", "password", "123"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.name").exists()
                    .jsonPath("$.errors.password").exists();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousUpdate() {
            client.put()
                    .uri("/api/users/{id}", alice.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("name", "Anonymous"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("deletion")
    class Deletion {

        @Test
        @DisplayName("admin deletes a user — active tasks return to the pool")
        void adminDeletesUserAndReleasesActiveTasks() {
            UUID todoTask = createTask(admin, "Not started", alice.id());
            UUID inProgressTask = createTask(admin, "In progress", alice.id());
            startTask(alice, inProgressTask);
            UUID foreignTask = createTask(admin, "Bob's task", bob.id());

            client.delete()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertThat(userRepository.findById(alice.id())).isEmpty();

            Task firstReleased = taskRepository.findById(todoTask).orElseThrow();
            assertThat(firstReleased.getOwner()).isNull();
            assertThat(firstReleased.getStatus()).isEqualTo(TaskStatus.TO_DO);

            Task secondReleased = taskRepository.findById(inProgressTask).orElseThrow();
            assertThat(secondReleased.getOwner()).isNull();
            assertThat(secondReleased.getStatus())
                    .as("a task in IN_PROGRESS also returns to TO_DO")
                    .isEqualTo(TaskStatus.TO_DO);

            Task untouched = taskRepository.findById(foreignTask).orElseThrow();
            assertThat(untouched.getOwner()).isNotNull();
            assertThat(untouched.getOwner().getId()).isEqualTo(bob.id());
        }

        @Test
        @DisplayName("a deleted user's finished task stays DONE but loses its owner")
        void keepsDoneTasksButClearsOwner() {
            UUID doneTask = createTask(admin, "Finished", alice.id());
            startTask(alice, doneTask);
            client.put()
                    .uri("/api/tasks/{id}", doneTask)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("status", "DONE"))
                    .exchange()
                    .expectStatus().isOk();

            client.delete()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNoContent();

            client.get()
                    .uri("/api/tasks/{id}", doneTask)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("DONE")
                    .jsonPath("$.ownerId").doesNotExist();
        }

        @Test
        @DisplayName("a user cannot delete anyone — 403")
        void userCannotDelete() {
            client.delete()
                    .uri("/api/users/{id}", bob.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isForbidden();

            client.delete()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isForbidden();

            assertThat(userRepository.findById(alice.id())).isPresent();
        }

        @Test
        @DisplayName("a non-existent user — 404")
        void returnsNotFoundForUnknownUser() {
            UUID missing = UUID.randomUUID();

            client.delete()
                    .uri("/api/users/{id}", missing)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("User not found: " + missing);
        }

        @Test
        @DisplayName("once a user is deleted their token stops working — 401")
        void tokenOfDeletedUserStopsWorking() {
            client.delete()
                    .uri("/api/users/{id}", alice.id())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNoContent();

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousDeletion() {
            client.delete()
                    .uri("/api/users/{id}", alice.id())
                    .exchange()
                    .expectStatus().isUnauthorized();

            assertThat(userRepository.findById(alice.id())).isPresent();
        }
    }
}
