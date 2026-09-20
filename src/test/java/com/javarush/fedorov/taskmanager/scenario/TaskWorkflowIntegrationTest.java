package com.javarush.fedorov.taskmanager.scenario;

import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("End-to-end team workflow scenarios")
class TaskWorkflowIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("task lifecycle: pool → assignee → back to the pool → another assignee → done → deletion")
    void fullTaskLifecycle() {
        Actor admin = createAdmin("lead@workflow.test");

        client.post()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "Alice", "email", "alice@workflow.test", "password", DEFAULT_PASSWORD))
                .exchange()
                .expectStatus().isCreated();

        client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "Bob", "email", "bob@workflow.test", "password", DEFAULT_PASSWORD))
                .exchange()
                .expectStatus().isCreated();

        String aliceToken = "Bearer " + login("alice@workflow.test", DEFAULT_PASSWORD);
        String bobToken = "Bearer " + login("bob@workflow.test", DEFAULT_PASSWORD);
        UUID aliceId = userRepository.findByEmail("alice@workflow.test").orElseThrow().getId();
        UUID bobId = userRepository.findByEmail("bob@workflow.test").orElseThrow().getId();

        RestTestClient.ResponseSpec created = client.post()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "Prepare release", "deadline", futureDeadline()))
                .exchange()
                .expectStatus().isCreated();
        UUID taskId = UUID.fromString(bodyOf(created).read("$.id", String.class));

        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("userId", aliceId, "status", "IN_PROGRESS"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ownerId").isEqualTo(aliceId.toString())
                .jsonPath("$.status").isEqualTo("IN_PROGRESS");

        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "Prepare release 1.0"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Prepare release 1.0");

        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "Takeover"))
                .exchange()
                .expectStatus().isForbidden();

        client.post()
                .uri("/api/tasks/{id}/release", taskId)
                .header(HttpHeaders.AUTHORIZATION, aliceToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("TO_DO")
                .jsonPath("$.ownerId").doesNotExist();

        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("userId", bobId, "status", "IN_PROGRESS"))
                .exchange()
                .expectStatus().isOk();

        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("status", "DONE"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DONE")
                .jsonPath("$.ownerName").isEqualTo("Bob");

        client.get()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, aliceToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].status").isEqualTo("DONE")
                .jsonPath("$[0].ownerId").isEqualTo(bobId.toString());

        client.post()
                .uri("/api/tasks/{id}/release", taskId)
                .header(HttpHeaders.AUTHORIZATION, bobToken)
                .exchange()
                .expectStatus().isBadRequest();

        client.delete()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, bobToken)
                .exchange()
                .expectStatus().isForbidden();

        client.delete()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .exchange()
                .expectStatus().isNoContent();

        client.get()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .exchange()
                .expectStatus().isNotFound();

        assertThat(taskRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("an employee leaves: their open tasks return to the pool and stay available to others")
    void tasksOfRemovedUserReturnToPool() {
        Actor admin = createAdmin("lead@offboarding.test");
        Actor leaving = createUser("leaving@offboarding.test");
        Actor staying = createUser("staying@offboarding.test");

        UUID notStarted = createTask(admin, "Not started", leaving.id());
        UUID inProgress = createTask(admin, "In progress", leaving.id());
        startTask(leaving, inProgress);

        client.delete()
                .uri("/api/users/{id}", leaving.id())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .exchange()
                .expectStatus().isNoContent();

        client.get()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, staying.bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[?(@.ownerId)]").isEmpty()
                .jsonPath("$[0].status").isEqualTo("TO_DO")
                .jsonPath("$[1].status").isEqualTo("TO_DO");

        client.put()
                .uri("/api/tasks/{id}", inProgress)
                .header(HttpHeaders.AUTHORIZATION, staying.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("userId", staying.id(), "status", "IN_PROGRESS"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ownerId").isEqualTo(staying.id().toString());

        client.get()
                .uri("/api/tasks/{id}", notStarted)
                .header(HttpHeaders.AUTHORIZATION, staying.bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ownerId").doesNotExist();
    }

    @Test
    @DisplayName("a previously issued token stays valid after a password change (stateless JWT)")
    void issuedTokenSurvivesPasswordChange() {
        Actor alice = createUser("alice@password.test");
        String oldToken = alice.bearer();

        client.put()
                .uri("/api/users/{id}", alice.id())
                .header(HttpHeaders.AUTHORIZATION, oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("password", "brandnewpass1"))
                .exchange()
                .expectStatus().isOk();

        client.get()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, oldToken)
                .exchange()
                .expectStatus().isOk();

        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("email", alice.email(), "password", "brandnewpass1"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("an administrator created through the API immediately has full rights")
    void createdAdminGetsFullRights() {
        Actor admin = createAdmin("lead@promotion.test");
        Actor alice = createUser("alice@promotion.test");
        UUID taskId = createTask(admin, "Alice's task", alice.id());

        client.delete()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .exchange()
                .expectStatus().isForbidden();

        client.post()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "New admin", "email", "newadmin@promotion.test",
                        "password", DEFAULT_PASSWORD, "role", "ADMIN"))
                .exchange()
                .expectStatus().isCreated();

        String newAdminToken = "Bearer " + login("newadmin@promotion.test", DEFAULT_PASSWORD);

        client.delete()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, newAdminToken)
                .exchange()
                .expectStatus().isNoContent();
    }
}
