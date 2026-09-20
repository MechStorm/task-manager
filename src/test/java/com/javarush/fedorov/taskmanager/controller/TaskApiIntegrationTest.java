package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("/api/tasks — CRUD, permissions and status transitions")
class TaskApiIntegrationTest extends IntegrationTestBase {

    private Actor admin;
    private Actor alice;
    private Actor bob;

    @BeforeEach
    void createActors() {
        admin = createAdmin("admin@tasks.test");
        alice = createUser("alice@tasks.test");
        bob = createUser("bob@tasks.test");
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("without userId a task lands in the shared pool with status TO_DO")
        void createsUnassignedTask() {
            Instant deadline = futureDeadline();

            RestTestClient.ResponseSpec response = client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Task from the pool", "deadline", deadline))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location).contains("/api/tasks/"));

            response.expectBody()
                    .jsonPath("$.title").isEqualTo("Task from the pool")
                    .jsonPath("$.status").isEqualTo("TO_DO")
                    .jsonPath("$.ownerId").doesNotExist()
                    .jsonPath("$.ownerName").doesNotExist()
                    .jsonPath("$.createdAt").exists();

            UUID id = UUID.fromString(bodyOf(response).read("$.id", String.class));
            Task saved = taskRepository.findById(id).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(TaskStatus.TO_DO);
            assertThat(saved.getOwner()).isNull();
            assertThat(saved.getDeadline()).isEqualTo(deadline);
        }

        @Test
        @DisplayName("a user may assign a task to themselves right away")
        void createsSelfAssignedTask() {
            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "My task", "userId", alice.id()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.ownerId").isEqualTo(alice.id().toString())
                    .jsonPath("$.ownerName").isEqualTo(alice.name())
                    .jsonPath("$.deadline").doesNotExist();
        }

        @Test
        @DisplayName("a user cannot assign a task to someone else — 403")
        void userCannotAssignToSomeoneElse() {
            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Someone else's task", "userId", bob.id()))
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Only admin can assign tasks to other users");

            assertThat(taskRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("admin may assign a task to any user")
        void adminAssignsToAnyUser() {
            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Task for Bob", "userId", bob.id()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.ownerId").isEqualTo(bob.id().toString());
        }

        @Test
        @DisplayName("assigning to a non-existent user — 404")
        void rejectsUnknownAssignee() {
            UUID missing = UUID.randomUUID();

            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Task into the void", "userId", missing))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("User not found: " + missing);
        }

        @Test
        @DisplayName("a deadline in the past — 400")
        void rejectsPastDeadline() {
            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Overdue", "deadline", pastDeadline()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.deadline").isEqualTo("Deadline can't be in the past");
        }

        @Test
        @DisplayName("a blank title — 400")
        void rejectsBlankTitle() {
            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "   "))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.title").isEqualTo("Title can't be blank");
        }

        @Test
        @DisplayName("a title longer than 255 characters — 400")
        void rejectsTooLongTitle() {
            client.post()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "x".repeat(256)))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.title").exists();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousCreation() {
            client.post()
                    .uri("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Anonymous task"))
                    .exchange()
                    .expectStatus().isUnauthorized();

            assertThat(taskRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("the task list is visible to any authenticated user")
        void listsAllTasks() {
            createTask(admin, "Alice's task", alice.id());
            createTask(admin, "Bob's task", bob.id());
            createUnassignedTask(admin, "Task from the pool");

            client.get()
                    .uri("/api/tasks")
                    .header(HttpHeaders.AUTHORIZATION, bob.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(3);
        }

        @Test
        @DisplayName("a task read by id comes back with owner data")
        void findsTaskById() {
            UUID taskId = createTask(admin, "Alice's task", alice.id());

            client.get()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, bob.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(taskId.toString())
                    .jsonPath("$.title").isEqualTo("Alice's task")
                    .jsonPath("$.ownerId").isEqualTo(alice.id().toString())
                    .jsonPath("$.ownerName").isEqualTo(alice.name());
        }

        @Test
        @DisplayName("a non-existent id — 404")
        void returnsNotFoundForUnknownId() {
            UUID missing = UUID.randomUUID();

            client.get()
                    .uri("/api/tasks/{id}", missing)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Task with id " + missing + " not found");
        }

        @Test
        @DisplayName("an id that is not a UUID — 400")
        void returnsBadRequestForMalformedId() {
            client.get()
                    .uri("/api/tasks/not-a-uuid")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousReading() {
            client.get()
                    .uri("/api/tasks")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("updating")
    class Updating {

        @Test
        @DisplayName("the owner changes title and deadline")
        void ownerUpdatesContent() {
            UUID taskId = createTask(alice, "Old title", alice.id());
            Instant newDeadline = futureDeadline().plusSeconds(3600);

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "New title", "deadline", newDeadline))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("New title")
                    .jsonPath("$.deadline").exists();

            Task updated = taskRepository.findById(taskId).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("New title");
            assertThat(updated.getDeadline()).isEqualTo(newDeadline);
        }

        @Test
        @DisplayName("a user does not touch another user's assigned task — 403")
        void userCannotTouchForeignTask() {
            UUID taskId = createTask(admin, "Alice's task", alice.id());

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, bob.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Hijacked"))
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("You can only modify your own or unassigned tasks");

            assertThat(taskRepository.findById(taskId).orElseThrow().getTitle()).isEqualTo("Alice's task");
        }

        @Test
        @DisplayName("admin edits any task")
        void adminUpdatesAnyTask() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Fixed by the admin"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Fixed by the admin");
        }

        @Test
        @DisplayName("the content of a free task cannot be edited without claiming it — 403")
        void cannotEditContentOfUnassignedTask() {
            UUID taskId = createUnassignedTask(alice, "Free task");

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Renamed without claiming"))
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("You can only edit tasks you own");
        }

        @Test
        @DisplayName("a user claims a free task")
        void userClaimsUnassignedTask() {
            UUID taskId = createUnassignedTask(admin, "Free task");

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("userId", alice.id()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.ownerId").isEqualTo(alice.id().toString())
                    .jsonPath("$.status").isEqualTo("TO_DO");
        }

        @Test
        @DisplayName("claiming a task and starting work fits into one request")
        void userClaimsAndStartsInOneRequest() {
            UUID taskId = createUnassignedTask(admin, "Free task");

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("userId", alice.id(), "status", "IN_PROGRESS"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.ownerId").isEqualTo(alice.id().toString())
                    .jsonPath("$.status").isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("a user cannot reassign a task to someone else — 403")
        void userCannotReassignToSomeoneElse() {
            UUID taskId = createTask(alice, "My task", alice.id());

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("userId", bob.id()))
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Only admin can assign tasks to other users");
        }

        @Test
        @DisplayName("admin reassigns a task to another user")
        void adminReassignsTask() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("userId", bob.id()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.ownerId").isEqualTo(bob.id().toString());
        }

        @Test
        @DisplayName("reassigning to a non-existent user — 404")
        void rejectsReassignToUnknownUser() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());
            UUID missing = UUID.randomUUID();

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("userId", missing))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("User not found: " + missing);
        }

        @Test
        @DisplayName("an empty body changes nothing but still goes through the access check")
        void emptyPayloadIsNoOp() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Alice's task")
                    .jsonPath("$.status").isEqualTo("TO_DO");
        }

        @Test
        @DisplayName("a blank title and a past deadline — 400")
        void validatesPayload() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "", "deadline", pastDeadline()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.title").exists()
                    .jsonPath("$.errors.deadline").exists();
        }

        @Test
        @DisplayName("a non-existent task — 404")
        void returnsNotFoundForUnknownTask() {
            client.put()
                    .uri("/api/tasks/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Does not matter"))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousUpdate() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("title", "Anonymous"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("TO_DO → IN_PROGRESS → DONE goes through")
        void walksThroughHappyPath() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            updateStatus(taskId, "IN_PROGRESS").expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("IN_PROGRESS");

            updateStatus(taskId, "DONE").expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("DONE");
        }

        @Test
        @DisplayName("TO_DO → DONE is rejected — 400")
        void rejectsSkippingInProgress() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            updateStatus(taskId, "DONE")
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Can't move task from TO_DO to DONE");
        }

        @Test
        @DisplayName("DONE → IN_PROGRESS is allowed (a task can be reopened)")
        void allowsReopening() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());
            updateStatus(taskId, "IN_PROGRESS").expectStatus().isOk();
            updateStatus(taskId, "DONE").expectStatus().isOk();

            updateStatus(taskId, "IN_PROGRESS")
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("DONE → TO_DO is rejected — 400")
        void rejectsDoneToTodo() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());
            updateStatus(taskId, "IN_PROGRESS").expectStatus().isOk();
            updateStatus(taskId, "DONE").expectStatus().isOk();

            updateStatus(taskId, "TO_DO")
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Can't move task from DONE to TO_DO");
        }

        @Test
        @DisplayName("setting the same status again is idempotent")
        void sameStatusIsIdempotent() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            updateStatus(taskId, "TO_DO")
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("TO_DO");
        }

        @Test
        @DisplayName("a task without an owner cannot be started — 400")
        void rejectsInProgressWithoutOwner() {
            UUID taskId = createUnassignedTask(admin, "Free task");

            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("status", "IN_PROGRESS"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Can't move task to IN_PROGRESS because owner is null");
        }

        @Test
        @DisplayName("an unknown status value — 400")
        void rejectsUnknownStatus() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            updateStatus(taskId, "CANCELLED")
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Malformed JSON request");
        }

        private RestTestClient.ResponseSpec updateStatus(UUID taskId, String status) {
            return client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("status", status))
                    .exchange();
        }
    }

    @Nested
    @DisplayName("releasing a task to the pool")
    class Releasing {

        @Test
        @DisplayName("the owner releases a task to the pool: owner cleared, status TO_DO")
        void ownerReleasesTask() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());
            startTask(alice, taskId);

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("TO_DO")
                    .jsonPath("$.ownerId").doesNotExist();

            Task released = taskRepository.findById(taskId).orElseThrow();
            assertThat(released.getOwner()).isNull();
            assertThat(released.getStatus()).isEqualTo(TaskStatus.TO_DO);
        }

        @Test
        @DisplayName("admin releases a task owned by someone else")
        void adminReleasesForeignTask() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.ownerId").doesNotExist();
        }

        @Test
        @DisplayName("a non-owner gets 403")
        void nonOwnerCannotRelease() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .header(HttpHeaders.AUTHORIZATION, bob.bearer())
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("You can only edit tasks you own");
        }

        @Test
        @DisplayName("admin releases an already free task — 400 instead of a server crash")
        void adminReleasingUnassignedTaskIsRejected() {
            UUID taskId = createUnassignedTask(admin, "Free task");

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Task has no owner and is already in the pool");
        }

        @Test
        @DisplayName("a finished task without an owner cannot be released either — 400")
        void adminCannotReleaseOwnerlessDoneTask() {
            UUID taskId = createTask(admin, "Finished and orphaned", alice.id());
            startTask(alice, taskId);
            client.put()
                    .uri("/api/tasks/{id}", taskId)
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

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("a user cannot release a free task — 403")
        void userCannotReleaseUnassignedTask() {
            UUID taskId = createUnassignedTask(admin, "Free task");

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("a finished task cannot be released to the pool — 400")
        void cannotReleaseDoneTask() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());
            startTask(alice, taskId);
            client.put()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("status", "DONE"))
                    .exchange()
                    .expectStatus().isOk();

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Completed task can't be released back to the pool");
        }

        @Test
        @DisplayName("a non-existent task — 404")
        void returnsNotFoundForUnknownTask() {
            client.post()
                    .uri("/api/tasks/{id}/release", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousRelease() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.post()
                    .uri("/api/tasks/{id}/release", taskId)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("deletion")
    class Deletion {

        @Test
        @DisplayName("admin deletes a task — 204, reading it again gives 404")
        void adminDeletesTask() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.delete()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertThat(taskRepository.findById(taskId)).isEmpty();

            client.get()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("a user cannot delete even their own task — 403")
        void userCannotDeleteOwnTask() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.delete()
                    .uri("/api/tasks/{id}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .exchange()
                    .expectStatus().isForbidden();

            assertThat(taskRepository.findById(taskId)).isPresent();
        }

        @Test
        @DisplayName("a non-existent task — 404")
        void returnsNotFoundForUnknownTask() {
            client.delete()
                    .uri("/api/tasks/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("without a token — 401")
        void rejectsAnonymousDeletion() {
            UUID taskId = createTask(alice, "Alice's task", alice.id());

            client.delete()
                    .uri("/api/tasks/{id}", taskId)
                    .exchange()
                    .expectStatus().isUnauthorized();

            assertThat(taskRepository.findById(taskId)).isPresent();
        }
    }
}
