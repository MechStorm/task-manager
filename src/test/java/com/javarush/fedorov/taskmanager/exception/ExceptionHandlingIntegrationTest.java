package com.javarush.fedorov.taskmanager.exception;

import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.repository.TaskRepository;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DisplayName("GlobalExceptionHandler — infrastructure failures that the real API cannot provoke")
class ExceptionHandlingIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private TaskRepository mockedTaskRepository;

    private Actor alice;

    @BeforeEach
    void createActor() {
        alice = createUser("alice@errors.test");
    }

    @Test
    @DisplayName("a concurrent modification turns into 409 with a retry hint")
    void mapsOptimisticLockingFailureToConflict() {
        UUID taskId = UUID.randomUUID();
        given(mockedTaskRepository.findById(taskId)).willReturn(Optional.of(taskOwnedByAlice(taskId)));
        given(mockedTaskRepository.saveAndFlush(any(Task.class)))
                .willThrow(new OptimisticLockingFailureException("Row was updated by another transaction"));

        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "New title"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").isEqualTo("Task was modified by another user. Reload it and try again");
    }

    @Test
    @DisplayName("a unique constraint violation turns into 409")
    void mapsDataIntegrityViolationToConflict() {
        given(mockedTaskRepository.saveAndFlush(any(Task.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        client.post()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "Task"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").isEqualTo("Request violates unique constraint violation");
    }

    @Test
    @DisplayName("an unexpected failure turns into 500 and leaks nothing about its cause")
    void mapsUnexpectedFailureToServerError() {
        given(mockedTaskRepository.findAll())
                .willThrow(new IllegalStateException("connection pool exhausted at db-primary:5432"));

        client.get()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.detail").isEqualTo("Internal Server Error")
                .consumeWith(result -> {
                    byte[] body = result.getResponseBody();
                    assertThat(new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8))
                            .as("the response must not expose internals or a stack trace")
                            .doesNotContain("connection pool exhausted")
                            .doesNotContain("db-primary")
                            .doesNotContain("IllegalStateException");
                });
    }

    private Task taskOwnedByAlice(UUID taskId) {
        Task task = new Task();
        task.setId(taskId);
        task.setTitle("Old title");
        task.setStatus(TaskStatus.TO_DO);
        task.setOwner(userRepository.findById(alice.id()).orElseThrow());
        return task;
    }
}
