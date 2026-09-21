package com.javarush.fedorov.taskmanager.metrics;

import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Business metrics — counters driven through the API")
class BusinessMetricsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MeterRegistry registry;

    private Actor admin;
    private Actor alice;

    @BeforeEach
    void createActors() {
        admin = createAdmin("admin@metrics.test");
        alice = createUser("alice@metrics.test");
    }

    @Nested
    @DisplayName("tasks")
    class Tasks {

        @Test
        @DisplayName("creating a task increments tasks.creations")
        void countsCreation() {
            double before = count("tasks.creations");

            createUnassignedTask(alice, "Counted task");

            assertThat(count("tasks.creations")).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("a rejected creation is not counted")
        void ignoresRejectedCreation() {
            double before = count("tasks.creations");

            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Overdue", "deadline", pastDeadline()))
                    .exchange()
                    .expectStatus().isBadRequest();

            assertThat(count("tasks.creations")).isEqualTo(before);
        }

        @Test
        @DisplayName("an admin deletion increments tasks.deleted, a forbidden one does not")
        void countsDeletion() {
            UUID taskId = createUnassignedTask(alice, "Task to delete");
            double before = count("tasks.deleted");

            delete(alice, taskId).expectStatus().isForbidden();
            assertThat(count("tasks.deleted")).isEqualTo(before);

            delete(admin, taskId).expectStatus().isNoContent();
            assertThat(count("tasks.deleted")).isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("starting a task counts TO_DO → IN_PROGRESS")
        void countsStart() {
            UUID taskId = createTask(alice, "Task", alice.id());
            double before = transitions(TaskStatus.TO_DO, TaskStatus.IN_PROGRESS);

            startTask(alice, taskId);

            assertThat(transitions(TaskStatus.TO_DO, TaskStatus.IN_PROGRESS)).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("repeating the current status or editing other fields counts no transition")
        void ignoresUpdatesWithoutStatusChange() {
            UUID taskId = createTask(alice, "Task", alice.id());
            double before = allTransitions();

            update(alice, taskId, json("status", "TO_DO")).expectStatus().isOk();
            update(alice, taskId, json("title", "Renamed task")).expectStatus().isOk();

            assertThat(allTransitions()).isEqualTo(before);
        }

        @Test
        @DisplayName("a rejected transition counts nothing")
        void ignoresRejectedTransition() {
            UUID taskId = createTask(alice, "Task", alice.id());
            double before = allTransitions();

            update(alice, taskId, json("status", "DONE")).expectStatus().isBadRequest();

            assertThat(allTransitions()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("releasing to the pool")
    class Releasing {

        @Test
        @DisplayName("releasing a task in progress counts the release and IN_PROGRESS → TO_DO")
        void countsReleaseWithTransition() {
            UUID taskId = createTask(alice, "Task", alice.id());
            startTask(alice, taskId);
            double released = count("tasks.released");
            double backToTodo = transitions(TaskStatus.IN_PROGRESS, TaskStatus.TO_DO);

            release(alice, taskId).expectStatus().isOk();

            assertThat(count("tasks.released")).isEqualTo(released + 1);
            assertThat(transitions(TaskStatus.IN_PROGRESS, TaskStatus.TO_DO)).isEqualTo(backToTodo + 1);
        }

        @Test
        @DisplayName("releasing a task still in TO_DO counts the release but no transition")
        void countsReleaseWithoutTransition() {
            UUID taskId = createTask(alice, "Task", alice.id());
            double released = count("tasks.released");
            double transitions = allTransitions();

            release(alice, taskId).expectStatus().isOk();

            assertThat(count("tasks.released")).isEqualTo(released + 1);
            assertThat(allTransitions()).isEqualTo(transitions);
        }

        @Test
        @DisplayName("a rejected release counts nothing")
        void ignoresRejectedRelease() {
            UUID taskId = createUnassignedTask(admin, "Free task");
            double released = count("tasks.released");
            double transitions = allTransitions();

            release(admin, taskId).expectStatus().isBadRequest();

            assertThat(count("tasks.released")).isEqualTo(released);
            assertThat(allTransitions()).isEqualTo(transitions);
        }
    }

    @Nested
    @DisplayName("logins")
    class Logins {

        @Test
        @DisplayName("a successful login increments result=success")
        void countsSuccessfulLogin() {
            double before = logins("success");

            login(alice.email(), DEFAULT_PASSWORD);

            assertThat(logins("success")).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("a wrong password and an unknown email both increment result=failure")
        void countsFailedLogins() {
            double success = logins("success");
            double failure = logins("failure");

            attemptLogin(alice.email(), "wrong-password").expectStatus().isUnauthorized();
            attemptLogin("nobody@metrics.test", DEFAULT_PASSWORD).expectStatus().isUnauthorized();

            assertThat(logins("failure")).isEqualTo(failure + 2);
            assertThat(logins("success")).isEqualTo(success);
        }

        @Test
        @DisplayName("requests authenticated with a JWT are not counted as logins")
        void ignoresBearerRequests() {
            double before = logins("success");

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isOk();

            assertThat(logins("success")).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("/actuator/prometheus exports business metrics with the application tag and snake_case labels")
    void exportsBusinessMetrics() {
        byte[] body = client.get()
                .uri("/actuator/prometheus")
                .accept(MediaType.TEXT_PLAIN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8))
                .contains("tasks_creations_total{application=\"task-manager-app\"}")
                .contains("tasks_released_total{application=\"task-manager-app\"}")
                .contains("tasks_deleted_total{application=\"task-manager-app\"}")
                .contains("tasks_status_transitions_total{application=\"task-manager-app\","
                        + "new_status=\"IN_PROGRESS\",prev_status=\"TO_DO\"}")
                .contains("auth_login_total{application=\"task-manager-app\",result=\"success\"}")
                .contains("auth_login_total{application=\"task-manager-app\",result=\"failure\"}");
    }

    private double count(String name) {
        return registry.get(name).counter().count();
    }

    private double transitions(TaskStatus prevStatus, TaskStatus newStatus) {
        return registry.get("tasks.status.transitions")
                .tag("prev_status", prevStatus.name())
                .tag("new_status", newStatus.name())
                .counter()
                .count();
    }

    private double allTransitions() {
        return registry.find("tasks.status.transitions").counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private double logins(String result) {
        return registry.get("auth.login").tag("result", result).counter().count();
    }

    private RestTestClient.ResponseSpec update(Actor actor, UUID taskId, String body) {
        return client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, actor.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec release(Actor actor, UUID taskId) {
        return client.post()
                .uri("/api/tasks/{id}/release", taskId)
                .header(HttpHeaders.AUTHORIZATION, actor.bearer())
                .exchange();
    }

    private RestTestClient.ResponseSpec delete(Actor actor, UUID taskId) {
        return client.delete()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, actor.bearer())
                .exchange();
    }

    private RestTestClient.ResponseSpec attemptLogin(String email, String password) {
        return client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("email", email, "password", password))
                .exchange();
    }
}
